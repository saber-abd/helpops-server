# 📦 Installation de Maven et configuration de l'environnement

Ce guide vous explique comment installer Maven depuis zéro, le configurer, et compiler/tester les 4 projets HELP'OPS.

---

## 1. 📥 Télécharger Maven

1. Allez sur : https://maven.apache.org/download.cgi
2. Téléchargez le fichier **Binary zip archive** (ex: `apache-maven-3.9.9-bin.zip`)

---

## 2. 📂 Installer Maven

1. **Extraire le zip** dans un dossier sans espaces, par exemple :
   ```
   C:\Tools\apache-maven-3.9.9
   ```

2. **Vérifier** que vous avez ce fichier :
   ```
   C:\Tools\apache-maven-3.9.9\bin\mvn.cmd
   ```

---

## 3. ⚙️ Ajouter Maven au PATH (Windows)

### Méthode 1 : Via l'interface graphique

1. Clic droit sur **Ce PC** → **Propriétés**
2. Cliquez sur **Paramètres système avancés**
3. Cliquez sur **Variables d'environnement**
4. Dans **Variables système**, cherchez `Path` et cliquez sur **Modifier**
5. Cliquez sur **Nouveau** et ajoutez :
   ```
   C:\Tools\apache-maven-3.9.9\bin
   ```
6. Cliquez sur **OK** partout
7. **Redémarrez PowerShell** (ou tout terminal ouvert)

### Méthode 2 : Via PowerShell (administrateur)

```powershell
[System.Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\Tools\apache-maven-3.9.9\bin", "Machine")
```

---

## 4. ✅ Vérifier l'installation

Ouvrez un **nouveau** terminal PowerShell et tapez :

```powershell
mvn -version
```

Vous devriez voir quelque chose comme :
```
Apache Maven 3.9.9
Maven home: C:\Tools\apache-maven-3.9.9
Java version: 17.0.x
```

**Si vous voyez ça, Maven est prêt ! ✅**

---

## 5. 📁 Créer un dossier de travail et cloner les projets

```powershell
# Créer un dossier pour tous les projets
mkdir C:\Users\VotreNom\helpops-projet
cd C:\Users\VotreNom\helpops-projet

# Cloner les 4 dépôts GitHub
git clone https://github.com/saber-abd/helpops-interfaces.git
git clone https://github.com/saber-abd/helpops-auth.git
git clone https://github.com/saber-abd/helpops-server.git
git clone https://github.com/saber-abd/helpops-client.git
```

Vous devriez avoir cette structure :
```
helpops-projet/
├── helpops-interfaces/
├── helpops-auth/
├── helpops-server/
└── helpops-client/
```

---

## 6. 🔨 Compiler les projets avec Maven

### Ordre de compilation (IMPORTANT)

```powershell
# 1. Compiler et installer helpops-interfaces (dépendance des autres)
cd helpops-interfaces
mvn clean install

# 2. Compiler les 3 autres projets
cd ..\helpops-auth
mvn clean package

cd ..\helpops-server
mvn clean package

cd ..\helpops-client
mvn clean package
```

### Ce que fait Maven

- `mvn clean` : Supprime le dossier `target/` (nettoyage)
- `mvn install` : Compile + installe dans le dépôt local Maven (`~/.m2/repository/`)
- `mvn package` : Compile + crée le JAR dans `target/`

**Après cette étape, vous avez 3 fichiers JAR exécutables :**
- `helpops-auth\target\helpops-auth-1.0.0-jar-with-dependencies.jar`
- `helpops-server\target\helpops-server-1.0.0-jar-with-dependencies.jar`
- `helpops-client\target\helpops-client-1.0.0-jar-with-dependencies.jar`

---

## 7. 🚀 Tester le code sur le terminal

### Ouvrir 3 terminaux PowerShell

**Terminal 1 : Démarrer Auth (EN PREMIER)**
```powershell
cd C:\Users\VotreNom\helpops-projet\helpops-auth
java -jar target\helpops-auth-1.0.0-jar-with-dependencies.jar
```

Vous devriez voir :
```
[AuthServer] Serveur d'authentification demarre sur le port 2000
```

**Terminal 2 : Démarrer Server (EN SECOND)**
```powershell
cd C:\Users\VotreNom\helpops-projet\helpops-server
java -jar target\helpops-server-1.0.0-jar-with-dependencies.jar
```

Vous devriez voir :
```
[HelpOpsServer] Connexion a AuthService reussie
[HelpOpsServer] Serveur demarre sur le port 1099
```

**Terminal 3 : Démarrer Client**
```powershell
cd C:\Users\VotreNom\helpops-projet\helpops-client
java -jar target\helpops-client-1.0.0-jar-with-dependencies.jar
```

Vous devriez voir le menu :
```
=== HELP'OPS - Client ===
1. Connexion
2. Inscription
3. Quitter
```

### Tester une connexion

1. Tapez `1` (Connexion)
2. Login : `alice`
3. Mot de passe : `pass123`
4. Vous devriez voir le menu principal avec les options de gestion d'incidents

---

## 8. 🔄 Si vous modifiez le code

### Si vous modifiez helpops-interfaces
```powershell
cd helpops-interfaces
mvn clean install

# Puis recompiler LES TROIS autres projets
cd ..\helpops-auth && mvn clean package
cd ..\helpops-server && mvn clean package
cd ..\helpops-client && mvn clean package
```

### Si vous modifiez un autre projet (ex: helpops-auth)
```powershell
cd helpops-auth
mvn clean package
```

Puis **relancez** le JAR correspondant.

---

## 9. 🛠️ Commandes Maven utiles

| Commande | Description |
|---|---|
| `mvn clean` | Supprime le dossier `target/` |
| `mvn compile` | Compile les sources (sans créer de JAR) |
| `mvn package` | Compile + crée le JAR |
| `mvn install` | Compile + installe dans le dépôt local Maven |
| `mvn dependency:tree` | Affiche l'arbre des dépendances |

---

## ✅ Résumé rapide

```powershell
# Installation (une seule fois)
1. Télécharger Maven Binary zip
2. Extraire dans C:\Tools\
3. Ajouter C:\Tools\apache-maven-x.x.x\bin au PATH
4. Vérifier : mvn -version

# Compilation (à chaque modification)
cd helpops-interfaces && mvn clean install
cd ..\helpops-auth && mvn clean package
cd ..\helpops-server && mvn clean package
cd ..\helpops-client && mvn clean package

# Lancement (3 terminaux)
java -jar helpops-auth\target\helpops-auth-1.0.0-jar-with-dependencies.jar
java -jar helpops-server\target\helpops-server-1.0.0-jar-with-dependencies.jar
java -jar helpops-client\target\helpops-client-1.0.0-jar-with-dependencies.jar
```

**Vous êtes prêt ! 🎉**

