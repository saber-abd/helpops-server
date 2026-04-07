package helpops.server;

import helpops.interfaces.RMIAuthService;
import helpops.interfaces.RMIHelpOps;
import helpops.model.Incident;
import helpops.model.Statistiques;
import helpops.utils.DatabaseManager;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.*;

import static helpops.server.IncidentHelper.extraireIncident;

// Server de gestion des incidents.
public class HelpOpsServer extends UnicastRemoteObject implements RMIHelpOps {

    private RMIAuthService auth;
    private DatagramSocket udpSocket;

    public HelpOpsServer(String authHost, int authPort) throws RemoteException {
        super();
        try {
            Registry authRegistry = LocateRegistry.getRegistry(authHost, authPort);
            auth = (RMIAuthService) authRegistry.lookup("AuthService");
            System.out.println("[SERVER] Serveur Auth joint : " + auth.ping());
        } catch (Exception e) {
            System.err.println("[SERVER] Erreur liaison Auth : " + e.getMessage());
            System.exit(1);
        }
        try {
            this.udpSocket = new DatagramSocket();
        } catch (Exception e) {
            System.err.println("[SERVER] Erreur init UDP : " + e.getMessage());
        }
    }

    //  operations UTILISATEUR
    @Override
    public Incident signalerIncident(String tokenValeur, String categorie,
                                     String titre, String description) throws RemoteException {
        UUID userUuid = auth.getUuidDepuisToken(tokenValeur);
        if (userUuid == null) return null;
        String sql = "INSERT INTO incidents (user_uuid, categorie, titre, description) VALUES (?, ?, ?, ?) RETURNING id";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, userUuid);
            pstmt.setString(2, categorie);
            pstmt.setString(3, titre);
            pstmt.setString(4, description);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int idGenerated = rs.getInt(1);
                System.out.println("[SERVER] Incident #" + idGenerated + " cree pour " + userUuid);
                diffuserEvenementUDP("CRÉATION: Nouveau ticket #" + idGenerated + " signalé par l'utilisateur.");
                return new Incident(idGenerated, userUuid, categorie, titre, description);
            }
        } catch (SQLException e) {
            throw new RemoteException("Erreur creation incident : " + e.getMessage());
        }
        return null;
    }

    @Override
    public List<Incident> listerMesIncidents(String tokenValeur) throws RemoteException {
        UUID userUuid = auth.getUuidDepuisToken(tokenValeur);
        if (userUuid == null) return null;
        // fix v3 utilise extraireIncident pour avoir statut/agent/dates
        return recupererIncidents("SELECT * FROM incidents WHERE user_uuid = ? ORDER BY date_creation DESC", userUuid);
    }

    @Override
    public Incident consulterIncident(String tokenValeur, int id) throws RemoteException {
        UUID demandeurUuid = auth.getUuidDepuisToken(tokenValeur);
        String roleDemandeur = auth.getRoleDepuisToken(tokenValeur);
        if (demandeurUuid == null) return null;
        String sql = "SELECT * FROM incidents WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                UUID auteurUuid = (UUID) rs.getObject("user_uuid");
                if (demandeurUuid.equals(auteurUuid) || "AGENT".equalsIgnoreCase(roleDemandeur)) {
                    return extraireIncident(rs);
                } else {
                    System.out.println("[SERVER] Acces refuse pour l'ID " + id);
                }
            }
        } catch (SQLException e) {
            throw new RemoteException("Erreur consultation : " + e.getMessage());
        }
        return null;
    }

    //  operations AGENT lecture

    @Override
    public List<Incident> listerIncidentsOuverts(String tokenValeur) throws RemoteException {
        verifierAccesAgent(tokenValeur);
        return recupererIncidents("SELECT * FROM incidents WHERE statut = 'OPEN' ORDER BY date_creation", null);
    }

    @Override
    public List<Incident> listerMesAssignations(String tokenValeur) throws RemoteException {
        verifierAccesAgent(tokenValeur);
        UUID agentUuid = auth.getUuidDepuisToken(tokenValeur);
        return recupererIncidents("SELECT * FROM incidents WHERE agent_uuid = ? ORDER BY date_assignation DESC", agentUuid);
    }

    @Override
    public List<Incident> listerTousLesIncidents(String tokenValeur) throws RemoteException {
        verifierAccesAgent(tokenValeur);
        return recupererIncidents("SELECT * FROM incidents ORDER BY date_creation DESC", null);
    }

    //  operations AGENT ecriture avec transactions

    // v3.1 SELECT FOR UPDATE garantit qu'un seul agent peut prendre un ticket a la fois
    @Override
    public boolean prendreEnChargeIncident(String tokenValeur, int id) throws RemoteException {
        verifierAccesAgent(tokenValeur);
        UUID agentUuid = auth.getUuidDepuisToken(tokenValeur);

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // verrou exclusif sur la ligne : bloque tout autre SELECT FOR UPDATE concurrent
                String selectSql = "SELECT statut FROM incidents WHERE id = ? FOR UPDATE";
                PreparedStatement sel = conn.prepareStatement(selectSql);
                sel.setInt(1, id);
                ResultSet rs = sel.executeQuery();
                if (!rs.next()) {
                    conn.rollback();
                    throw new RemoteException("Incident #" + id + " introuvable.");
                }
                String statut = rs.getString("statut");
                // Verification metier (pas une erreur BD)
                if ("ASSIGNED".equalsIgnoreCase(statut)) {
                    conn.rollback();
                    throw new RemoteException("L'incident #" + id + " est deja pris en charge par un autre agent.");
                }
                if ("RESOLVED".equalsIgnoreCase(statut)) {
                    conn.rollback();
                    throw new RemoteException("L'incident #" + id + " est deja resolu.");
                }
                if (!"OPEN".equalsIgnoreCase(statut)) {
                    conn.rollback();
                    throw new RemoteException("L'incident #" + id + " ne peut pas etre pris en charge (statut: " + statut + ").");
                }
                String updateSql = "UPDATE incidents SET statut = 'ASSIGNED', agent_uuid = ?, " +
                                   "date_assignation = CURRENT_TIMESTAMP WHERE id = ?";
                PreparedStatement upd = conn.prepareStatement(updateSql);
                upd.setObject(1, agentUuid);
                upd.setInt(2, id);
                upd.executeUpdate();
                conn.commit();
                diffuserEvenementUDP("PRISE EN CHARGE: Le ticket #" + id + " a été assigné à l'agent [UUID: " + agentUuid + "].");
                System.out.println("[SERVER] Incident #" + id + " assigne a l'agent " + agentUuid);

                return true;
            } catch (RemoteException re) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw re;
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw new RemoteException("Erreur base de donnees : " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new RemoteException("Impossible d'obtenir une connexion BD : " + e.getMessage());
        }
    }

    // v3.1 cloture d'un ticket
    @Override
    public boolean resoudreTicket(String tokenValeur, int id, String messageResolution) throws RemoteException {
        verifierAccesAgent(tokenValeur);
        if (messageResolution == null || messageResolution.trim().isEmpty()) {
            throw new RemoteException("Le message de resolution est obligatoire.");
        }
        UUID agentUuid = auth.getUuidDepuisToken(tokenValeur);
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // verrou exclusif sur la ligne
                String selectSql = "SELECT statut, agent_uuid FROM incidents WHERE id = ? FOR UPDATE";
                PreparedStatement sel = conn.prepareStatement(selectSql);
                sel.setInt(1, id);
                ResultSet rs = sel.executeQuery();
                if (!rs.next()) {
                    conn.rollback();
                    throw new RemoteException("Ticket #" + id + " introuvable.");
                }
                String statut = rs.getString("statut");
                UUID agentAssigneUuid = (UUID) rs.getObject("agent_uuid");
                // verifications
                if ("OPEN".equalsIgnoreCase(statut)) {
                    conn.rollback();
                    throw new RemoteException("Le ticket #" + id + " n'est pas encore assigne (statut: OPEN).");
                }
                if ("RESOLVED".equalsIgnoreCase(statut)) {
                    conn.rollback();
                    throw new RemoteException("Le ticket #" + id + " est deja resolu.");
                }
                if (!"ASSIGNED".equalsIgnoreCase(statut)) {
                    conn.rollback();
                    throw new RemoteException("Le ticket #" + id + " ne peut pas etre resolu (statut: " + statut + ").");
                }
                if (agentAssigneUuid == null || !agentUuid.equals(agentAssigneUuid)) {
                    conn.rollback();
                    throw new RemoteException("Vous n'etes pas l'agent assigne a ce ticket. Resolution impossible.");
                }
                String updateSql = "UPDATE incidents SET statut = 'RESOLVED', " +
                                   "date_resolution = CURRENT_TIMESTAMP, message_resolution = ? WHERE id = ?";
                PreparedStatement upd = conn.prepareStatement(updateSql);
                upd.setString(1, messageResolution.trim());
                upd.setInt(2, id);
                upd.executeUpdate();
                conn.commit();
                diffuserEvenementUDP("RÉSOLUTION: Le ticket #" + id + " est désormais clos (Statut: RESOLVED).");
                System.out.println("[SERVER] Ticket #" + id + " resolu par l'agent " + agentUuid);
                return true;
            } catch (RemoteException re) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw re;
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw new RemoteException("Erreur base de donnees : " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new RemoteException("Impossible d'obtenir une connexion BD : " + e.getMessage());
        }
    }

    // v3.1 agent cree un ticket pour un utilisateur identifie par son login
    @Override
    public Incident creerTicketPourUtilisateur(String tokenValeur, String loginCible, String categorie, String titre, String description) throws RemoteException {
        verifierAccesAgent(tokenValeur);
        UUID targetUuid = auth.getUuidDepuisLogin(loginCible);
        if (targetUuid == null) {
            throw new RemoteException("Utilisateur '" + loginCible + "' introuvable.");
        }
        String sql = "INSERT INTO incidents (user_uuid, categorie, titre, description) VALUES (?, ?, ?, ?) RETURNING id";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, targetUuid);
            pstmt.setString(2, categorie);
            pstmt.setString(3, titre);
            pstmt.setString(4, description);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int idGenerated = rs.getInt(1);
                System.out.println("[SERVER] Ticket #" + idGenerated + " cree par agent pour l'utilisateur " + loginCible);
                diffuserEvenementUDP("CRÉATION TIERS: Ticket #" + idGenerated + " créé par un agent pour " + loginCible + ".");
                return new Incident(idGenerated, targetUuid, categorie, titre, description);
            }
        } catch (SQLException e) {
            throw new RemoteException("Erreur creation ticket : " + e.getMessage());
        }
        return null;
    }

    // ajout supplementaire -> deleguer un ticket
    @Override
    public boolean deleguerTicket(String tokenValeur, int id, String loginNouvelAgent) throws RemoteException {
        verifierAccesAgent(tokenValeur);
        UUID agentEmetteurUuid = auth.getUuidDepuisToken(tokenValeur);
        UUID nouvelAgentUuid = auth.getUuidDepuisLogin(loginNouvelAgent);

        if (nouvelAgentUuid == null) throw new RemoteException("Agent cible introuvable.");

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            String sql = "SELECT agent_uuid, statut FROM incidents WHERE id = ? FOR UPDATE";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                UUID actuel = (UUID) rs.getObject("agent_uuid");
                if (actuel != null && actuel.equals(agentEmetteurUuid)) {
                    String upd = "UPDATE incidents SET agent_uuid = ? WHERE id = ?";
                    PreparedStatement ups = conn.prepareStatement(upd);
                    ups.setObject(1, nouvelAgentUuid);
                    ups.setInt(2, id);
                    ups.executeUpdate();
                    conn.commit();

                    // Notification via le nouveau flux
                    diffuserEvenementUDP("DELEGATION: Ticket #" + id + " transféré à " + loginNouvelAgent);
                    return true;
                }
            }
            conn.rollback();
            return false;
        } catch (SQLException e) { throw new RemoteException(e.getMessage()); }
    }

    // v3.1 statistiques fonctionnement

    @Override
    public Statistiques getStatistiques(String tokenValeur) throws RemoteException {
        verifierAccesAgent(tokenValeur);
        Statistiques stats = new Statistiques();
        try (Connection conn = DatabaseManager.getConnection()) {
            // total des tickets
            try (ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM incidents")) {
                if (rs.next()) stats.setTotalTickets(rs.getInt(1));
            }
            // tickets resolus
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM incidents WHERE statut = 'RESOLVED'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.setTicketsResolus(rs.getInt(1));
            }
            // tickets par etat
            Map<String, Integer> parEtat = new HashMap<>();
            try (ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT statut, COUNT(*) FROM incidents GROUP BY statut ORDER BY statut")) {
                while (rs.next()) parEtat.put(rs.getString(1), rs.getInt(2));
            }
            stats.setTicketsParEtat(parEtat);
            // temps moyen OPEN -> RESOLVED (en heures)
            try (ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT AVG(EXTRACT(EPOCH FROM (date_resolution - date_creation)) / 3600) " +
                    "FROM incidents WHERE statut = 'RESOLVED' AND date_resolution IS NOT NULL")) {
                if (rs.next()) {
                    double val = rs.getDouble(1);
                    stats.setTempsMoyenResolutionHeures(rs.wasNull() ? 0.0 : val);
                }
            }
            // tickets par agent (via login)
            Map<String, Integer> parAgent = new HashMap<>();
            try (ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT u.login, COUNT(i.id) FROM incidents i " +
                    "JOIN users u ON i.agent_uuid = u.user_uuid " +
                    "WHERE i.agent_uuid IS NOT NULL GROUP BY u.login ORDER BY u.login")) {
                while (rs.next()) parAgent.put(rs.getString(1), rs.getInt(2));
            }
            stats.setTicketsParAgent(parAgent);
            // taux de pression = total tickets / nb agents actifs / nb jours activite
            int nbAgents = parAgent.size();
            if (nbAgents > 0) {
                double nbJours = 1.0;
                try (ResultSet rs = conn.createStatement().executeQuery(
                        "SELECT GREATEST(1, EXTRACT(DAY FROM (NOW() - MIN(date_creation))) + 1) FROM incidents")) {
                    if (rs.next()) nbJours = rs.getDouble(1);
                }
                stats.setTauxPression((double) stats.getTotalTickets() / nbAgents / nbJours);
            }

        } catch (SQLException e) {
            throw new RemoteException("Erreur calcul statistiques : " + e.getMessage());
        }

        return stats;
    }

    // V3.2 supervision

    private void diffuserEvenementUDP(String message) {
        if (udpSocket == null) return;
        try {
            byte[] data = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            InetAddress addr = InetAddress.getByName("localhost");
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, 5000);
            udpSocket.send(packet);
        } catch (Exception ex) {
            System.err.println("[ERREUR] Echec diffusion : " + ex.getMessage());
        }
    }

    //  helpers
    private void verifierAccesAgent(String token) throws RemoteException {
        String role = auth.getRoleDepuisToken(token);
        if (!"AGENT".equalsIgnoreCase(role)) {
            throw new RemoteException("Acces refuse : droits AGENT requis.");
        }
    }

    private List<Incident> recupererIncidents(String sql, Object param) throws RemoteException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return IncidentHelper.recupererListe(conn, sql, param);
        } catch (SQLException e) {
            throw new RemoteException("Erreur lecture incidents : " + e.getMessage());
        }
    }

    @Override
    public String ping() throws RemoteException {
        return "HelpOpsServer OK";
    }

    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            HelpOpsServer server = new HelpOpsServer("localhost", 1099);
            registry.rebind("HelpOps", server);
            System.out.println("[SERVER] Base PostgreSQL connectee.");
            System.out.println("[SERVER] Ecoute sur le port 1099.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
