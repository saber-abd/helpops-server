# 🌐 RMI : comprendre pourquoi et comment on l'utilise dans HELP'OPS

## 1. Qu'est-ce que RMI ?

**RMI (Remote Method Invocation)** est un mécanisme Java qui permet d'**appeler une méthode d'un objet situé sur une autre machine** comme si cet objet était local.

**Sans RMI :** Deux programmes sur des machines différentes doivent échanger des messages bruts (sockets, HTTP...) et écrire tout le code de sérialisation/désérialisation manuellement.

**Avec RMI :** Java s'occupe de tout. Tu écris `service.signalerIncident(...)` et Java envoie automatiquement les paramètres sur le réseau, exécute la méthode sur le serveur distant, et te renvoie le résultat.

---

## 2. Pourquoi on l'utilise dans HELP'OPS ?

Le projet exige une architecture **distribuée** : le client, le serveur principal et le serveur d'authentification sont sur des machines séparées (ou simulées localement).

**RMI est la technologie imposée par le cours MCPR** pour faire communiquer des programmes Java sur un réseau.

---

## 3. Les 4 éléments clés de RMI

### 🔌 L'interface Remote
C'est le **contrat** : elle définit quelles méthodes sont accessibles depuis le réseau.  
Chaque méthode doit déclarer `throws RemoteException` pour signaler qu'une erreur réseau est possible.

```java
// IHelpOps.java
public interface IHelpOps extends Remote {
    Incident signalerIncident(String token, String cat, String titre, String desc)
        throws RemoteException;
}
```

### 🖥️ L'implémentation (le serveur)
Le serveur **implémente** l'interface Remote et hérite de `UnicastRemoteObject`.  
`UnicastRemoteObject` fait le travail invisible : il crée un thread qui attend les appels réseau et les redirige vers les vraies méthodes.

```java
// HelpOpsServer.java
public class HelpOpsServer extends UnicastRemoteObject implements IHelpOps {
    public Incident signalerIncident(...) throws RemoteException {
        // Code qui s'exécute réellement sur le serveur
    }
}
```

### 📇 Le Registry (annuaire RMI)
C'est un **annuaire** : le serveur y dépose son objet sous un nom, le client le cherche par ce même nom.

```java
// Côté serveur : déposer l'objet
Registry registry = LocateRegistry.createRegistry(1099);
registry.rebind("HelpOps", server);

// Côté client : récupérer l'objet
Registry registry = LocateRegistry.getRegistry("localhost", 1099);
IHelpOps service = (IHelpOps) registry.lookup("HelpOps");
```

### 🔄 Le Stub (transparent, généré automatiquement)
Quand le client fait `service.signalerIncident(...)`, il ne parle pas directement au serveur.  
Java génère automatiquement un **stub** (un proxy local) qui intercepte l'appel, l'envoie sur le réseau, et renvoie la réponse. **Tout ça est invisible pour toi.**

---

## 4. Pourquoi les objets doivent être Serializable ?

Quand une méthode RMI retourne un objet (ex: `Incident`), Java doit le **convertir en suite d'octets** pour l'envoyer sur le réseau, puis le reconstituer de l'autre côté.  
C'est la **sérialisation**. Pour qu'un objet soit sérialisable, sa classe doit implémenter `Serializable`.

```java
public class Incident implements Serializable { ... }
public class Token    implements Serializable { ... }
public class User     implements Serializable { ... }
```

---

## 5. Ce qui est utilisé dans HELP'OPS

| Élément RMI | Utilisé ? | Où ? |
|---|---|---|
| Interface `Remote` | ✅ Oui | `IAuthService`, `IHelpOps` |
| `UnicastRemoteObject` | ✅ Oui | `AuthServer`, `HelpOpsServer` |
| Registry | ✅ Oui | Les deux serveurs + le client |
| `Serializable` | ✅ Oui | `Token`, `User`, `Incident` |
| `rmiregistry` (outil externe) | ❌ Non | On crée le registry en code avec `createRegistry()` |
| Stubs générés manuellement (rmic) | ❌ Non | Java moderne (>= 5) les génère automatiquement |
| Activation, JNDI | ❌ Non | Trop complexe, pas nécessaire |

---

## 6. Schéma de fonctionnement dans HELP'OPS

```
Machine "Auth"          Machine "Server"        Machine "Client"
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│  AuthServer      │    │  HelpOpsServer   │    │  HelpOpsClient   │
│  Registry :2000  │◄───│  (appelle Auth   │◄───│  (appelle Auth   │
│  "AuthService"   │    │   pour valider   │    │   pour login,    │
└──────────────────┘    │   les tokens)    │    │   puis HelpOps   │
                        │  Registry :1099  │    │   pour incidents)│
                        │  "HelpOps"       │    └──────────────────┘
                        └──────────────────┘
```

**En résumé :** RMI permet à chaque machine d'appeler des méthodes sur une autre machine **comme si c'était local**, sans écrire de code réseau manuel.
