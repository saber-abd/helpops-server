package helpops.server;

import helpops.model.Incident;
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
}