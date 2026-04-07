package helpops.server;

import helpops.model.Incident;
import helpops.model.Statistiques;

import java.sql.*;
import java.util.*;

public class IncidentHelper {

    public static Incident extraireIncident(ResultSet rs) throws SQLException {
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
        i.setDateAssignation(rs.getTimestamp("date_assignation"));
        i.setDateResolution(rs.getTimestamp("date_resolution"));
        i.setMessageResolution(rs.getString("message_resolution"));
        return i;
    }

    public static List<Incident> recupererListe(Connection conn, String sql, Object param) throws SQLException {
        List<Incident> liste = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (param != null) pstmt.setObject(1, param);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    liste.add(extraireIncident(rs));
                }
            }
        }
        return liste;
    }

    public static Statistiques calculerStatistiques(Connection conn) throws SQLException {
        Statistiques stats = new Statistiques();

        // total incident
        try (ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM incidents")) {
            if (rs.next()) stats.setTotalTickets(rs.getInt(1));
        }

        // tickets resolu
        try (ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM incidents WHERE statut = 'RESOLVED'")) {
            if (rs.next()) stats.setTicketsResolus(rs.getInt(1));
        }

        // Tickets (open + assigner)=> pour le calcul du taux de pression
        int backlog = 0;
        try (ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM incidents WHERE statut != 'RESOLVED'")) {
            if (rs.next()) backlog = rs.getInt(1);
        }

        // total agents de la table users
        int nbAgentsTotal = 0;
        try (ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM users WHERE role = 'AGENT'")) {
            if (rs.next()) nbAgentsTotal = rs.getInt(1);
        }

        // taux de pression
        stats.setTauxPression(nbAgentsTotal > 0 ? (double) backlog / nbAgentsTotal : 0.0);

        // temps moyen
        try (ResultSet rs = conn.createStatement().executeQuery(
                "SELECT AVG(EXTRACT(EPOCH FROM (date_resolution - date_creation))) " +
                        "FROM incidents WHERE statut = 'RESOLVED' AND date_resolution IS NOT NULL")) {
            if (rs.next()) stats.setTempsMoyenResolutionHeures(rs.getDouble(1));
        }

        // 7. ticket par etat
        Map<String, Integer> parEtat = new HashMap<>();
        try (ResultSet rs = conn.createStatement().executeQuery("SELECT statut, COUNT(*) FROM incidents GROUP BY statut")) {
            while (rs.next()) parEtat.put(rs.getString(1), rs.getInt(2));
        }
        stats.setTicketsParEtat(parEtat);

        return stats;
    }
}