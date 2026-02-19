package helpops.server;

import helpops.interfaces.IAuthService;
import helpops.interfaces.IHelpOps;
import helpops.model.Incident;

import java.io.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class HelpOpsServer extends UnicastRemoteObject implements IHelpOps {

    private static final String FICHIER_INCIDENTS = "incidents.dat";

    // Reference vers le serveur Auth (obtenue via RMI au demarrage)
    private IAuthService auth;

    private List<Incident> incidents;
    private AtomicInteger  compteurId;

    public HelpOpsServer(String authHost, int authPort) throws RemoteException {
        super();

        // Connexion au serveur Auth via RMI
        try {
            Registry authRegistry = LocateRegistry.getRegistry(authHost, authPort);
            auth = (IAuthService) authRegistry.lookup("AuthService");
            System.out.println("[SERVER] Serveur Auth joint : " + auth.ping());
        } catch (Exception e) {
            System.err.println("[SERVER] Impossible de joindre le serveur Auth ("
                + authHost + ":" + authPort + ") : " + e.getMessage());
            System.exit(1);
        }

        chargerIncidents();
        System.out.println("[SERVER] " + incidents.size() + " incident(s) charge(s).");
    }


    // Methodes RMI (definies dans IHelpOps)

    @Override
    public Incident signalerIncident(String tokenValeur, String categorie,
                                     String titre, String description) throws RemoteException {
        String login = loginDepuisToken(tokenValeur);
        if (login == null) return null;

        int id = compteurId.getAndIncrement();
        Incident incident = new Incident(id, categorie, titre, description, login);
        incidents.add(incident);
        sauvegarderIncidents();
        System.out.println("[SERVER] Incident #" + id + " cree par " + login);
        return incident;
    }

    @Override
    public List<Incident> listerMesIncidents(String tokenValeur) throws RemoteException {
        String login = loginDepuisToken(tokenValeur);
        if (login == null) return null;

        List<Incident> mes = new ArrayList<>();
        for (Incident i : incidents) {
            if (i.getLogin().equals(login)) mes.add(i);
        }
        System.out.println("[SERVER] " + login + " consulte sa liste (" + mes.size() + " incident(s))");
        return mes;
    }

    @Override
    public Incident consulterIncident(String tokenValeur, int id) throws RemoteException {
        String login = loginDepuisToken(tokenValeur);
        if (login == null) return null;

        for (Incident i : incidents) {
            if (i.getId() == id && i.getLogin().equals(login)) {
                System.out.println("[SERVER] " + login + " consulte incident #" + id);
                return i;
            }
        }
        System.out.println("[SERVER] Incident #" + id + " introuvable pour " + login);
        return null;
    }

    @Override
    public String ping() throws RemoteException {
        return "HelpOpsServer OK";
    }

    // Methode utilitaire : recupere le login depuis le token (via Auth)

    private String loginDepuisToken(String tokenValeur) throws RemoteException {
        String login = auth.getLoginDepuisToken(tokenValeur);
        if (login == null) {
            System.out.println("[SERVER] Token invalide ou expire");
        }
        return login;
    }

    // Save des incidents (serialisation Java)

    @SuppressWarnings("unchecked")
    private void chargerIncidents() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FICHIER_INCIDENTS))) {
            incidents  = (List<Incident>) ois.readObject();
            int dernierID = ois.readInt();
            compteurId = new AtomicInteger(dernierID + 1);
            System.out.println("[SERVER] Fichier incidents.dat lu.");
        } catch (Exception e) {
            incidents  = new ArrayList<>();
            compteurId = new AtomicInteger(1);
            System.out.println("[SERVER] Demarrage avec base vide.");
        }
    }

    private void sauvegarderIncidents() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FICHIER_INCIDENTS))) {
            oos.writeObject(incidents);
            oos.writeInt(compteurId.get() - 1);
        } catch (Exception e) {
            System.err.println("[SERVER] Erreur sauvegarde : " + e.getMessage());
        }
    }

    // Demarrage

    public static void main(String[] args) {
        // Adresse du serveur Auth (par defaut localhost, sinon 1er argument)
        String authHost = (args.length > 0) ? args[0] : "localhost";
        int    authPort = 2000;

        try {
            System.setProperty("file.encoding", "UTF-8");
            Registry registry = LocateRegistry.createRegistry(1099);
            System.out.println("[SERVER] Registry RMI cree sur le port 1099");

            HelpOpsServer server = new HelpOpsServer(authHost, authPort);
            registry.rebind("HelpOps", server);

            System.out.println("[SERVER] Service 'HelpOps' enregistre. En attente de connexions...");
        } catch (Exception e) {
            System.err.println("[SERVER] Erreur demarrage : " + e.getMessage());
            e.printStackTrace();
        }
    }
}
