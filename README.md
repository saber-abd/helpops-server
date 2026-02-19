# HELP'OPS - Version 2

Plateforme de gestion d'incidents distribuée basée sur RMI (Master 1 STRI - MCPR).

---

## 📦 Architecture : 4 dépôts GitHub

| Projet | Rôle | Port |
|---|---|---|
| [helpops-interfaces](https://github.com/saber-abd/helpops-interfaces) | Classes partagées + interfaces RMI | - |
| [helpops-auth](https://github.com/saber-abd/helpops-auth) | Serveur d'authentification | 2000 |
| [helpops-server](https://github.com/saber-abd/helpops-server) | Serveur principal (incidents) | 1099 |
| [helpops-client](https://github.com/saber-abd/helpops-client) | Client console | - |

---

## 🚀 Installation et lancement

### Prérequis
- Java 17+
- Maven 3.6+

**Voir [INSTALL_MAVEN.md](INSTALL_MAVEN.md) pour installer Maven depuis zéro.**

### Compilation
```bash
# 1. Cloner les 4 dépôts dans un même dossier
git clone https://github.com/saber-abd/helpops-interfaces.git
git clone https://github.com/saber-abd/helpops-auth.git
git clone https://github.com/saber-abd/helpops-server.git
git clone https://github.com/saber-abd/helpops-client.git

# 2. Compiler helpops-interfaces en premier (dépendance des autres)
cd helpops-interfaces
mvn clean install

# 3. Compiler les 3 autres
cd ..\helpops-auth && mvn clean package
cd ..\helpops-server && mvn clean package
cd ..\helpops-client && mvn clean package
```

### Lancement (3 terminaux séparés)
```bash
# Terminal 1 - Auth (démarrer EN PREMIER)
cd helpops-auth
java -jar target\helpops-auth-1.0.0-jar-with-dependencies.jar

# Terminal 2 - Server (démarrer EN SECOND)
cd helpops-server
java -jar target\helpops-server-1.0.0-jar-with-dependencies.jar

# Terminal 3 - Client
cd helpops-client
java -jar target\helpops-client-1.0.0-jar-with-dependencies.jar
```

---

## 📖 Documentation

| Fichier | Contenu |
|---|---|
| [INSTALL_MAVEN.md](INSTALL_MAVEN.md) | Installation de Maven + configuration PATH |
| [DOC_RMI.md](DOC_RMI.md) | Comprendre RMI : pourquoi et comment |
| [DOC_ALGORITHMES.md](DOC_ALGORITHMES.md) | Flux des appels, algorithmes, persistance |

---

## 👤 Comptes de test

Créés automatiquement au premier démarrage d'`helpops-auth` :

| Login   | Mot de passe |
|---------|--------------|
| alice   | pass123      |
| bob     | pass456      |
| charlie | pass789      |

Vous pouvez aussi créer votre propre compte depuis le menu du client.
