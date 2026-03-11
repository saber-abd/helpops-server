package helpops.server;

import helpops.interfaces.RMIAuthService;
import helpops.interfaces.RMIHelpOps;
import helpops.model.Incident;
import helpops.utils.DatabaseManager;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HelpOpsServer extends UnicastRemoteObject implements RMIHelpOps {
    private RMIAuthService auth;

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
    }

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
                System.out.println("[SERVER] Incident #" + idGenerated + " créé pour l'UUID " + userUuid);
                return new Incident(idGenerated, userUuid, categorie, titre, description);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;}

    @Override
    public List<Incident> listerMesIncidents(String tokenValeur) throws RemoteException {
        UUID userUuid = auth.getUuidDepuisToken(tokenValeur);
        if (userUuid == null) return null;
        List<Incident> mesIncidents = new ArrayList<>();
        String sql = "SELECT * FROM incidents WHERE user_uuid = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, userUuid);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                mesIncidents.add(new Incident(
                        rs.getInt("id"),
                        (UUID) rs.getObject("user_uuid"),
                        rs.getString("categorie"),
                        rs.getString("titre"),
                        rs.getString("description")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return mesIncidents;}

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
                    System.out.println("[SERVER] Accès refusé pour l'ID " + id );
                }}
        } catch (SQLException e) {
            e.printStackTrace();}
        return null;}

    @Override
    public List<Incident> listerIncidentsOuverts(String tokenValeur) throws RemoteException {
        verifierAccesAgent(tokenValeur);
        return recupererIncidents("SELECT * FROM incidents WHERE statut = 'OPEN'", null);
    }

    @Override
    public boolean prendreEnChargeIncident(String tokenValeur, int id) throws RemoteException {
        verifierAccesAgent(tokenValeur);
        UUID agentUuid = auth.getUuidDepuisToken(tokenValeur);
        String sql = "UPDATE incidents SET statut = 'ASSIGNED', agent_uuid = ?, date_assignation = CURRENT_TIMESTAMP " +
                "WHERE id = ? AND statut = 'OPEN'";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setObject(1, agentUuid);
            pstmt.setInt(2, id);
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new RemoteException("ÉCHEC : L'incident est déjà pris en charge ou n'existe pas.");
            }
            System.out.println("[SERVER] Incident #" + id + " assigné à l'agent " + agentUuid);
            return true;
        } catch (SQLException e) {
            throw new RemoteException("Erreur BDD lors de la prise en charge", e);
        }}

    @Override
    public List<Incident> listerMesAssignations(String tokenValeur) throws RemoteException {
        verifierAccesAgent(tokenValeur);
        UUID agentUuid = auth.getUuidDepuisToken(tokenValeur);
        return recupererIncidents("SELECT * FROM incidents WHERE agent_uuid = ?", agentUuid);
    }

    private void verifierAccesAgent(String token) throws RemoteException {
        String role = auth.getRoleDepuisToken(token);
        if (!"AGENT".equalsIgnoreCase(role)) {
            throw new RemoteException("Accès refusé : Droits AGENT requis.");
        }
    }

    private List<Incident> recupererIncidents(String sql, Object param) {
        List<Incident> liste = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (param != null) pstmt.setObject(1, param);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) { liste.add(extraireIncident(rs)); }
        } catch (SQLException e) { e.printStackTrace(); }
        return liste;
    }

    private Incident extraireIncident(ResultSet rs) throws SQLException {
        Incident i = new Incident(
                rs.getInt("id"),
                (UUID) rs.getObject("user_uuid"),
                rs.getString("categorie"),
                rs.getString("titre"),
                rs.getString("description")
        );
        i.setStatut(rs.getString("statut"));
        i.setAgentUuid((UUID) rs.getObject("agent_uuid"));
        i.setDateCreation(rs.getTimestamp("date_creation"));
        return i;
    }

    @Override
    public List<Incident> listerTousLesIncidents(String tokenValeur) throws RemoteException {
        verifierAccesAgent(tokenValeur);
        return recupererIncidents("SELECT * FROM incidents ORDER BY date_creation DESC", null);
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
            System.out.println("[SERVER] Base PostgreSQL connectée.");
            System.out.println("[SERVER] Ecoute sur le port 1099.");
        } catch (Exception e) {
            e.printStackTrace();
        }}
}