# ⚙️ HELP'OPS : fonctionnement des algorithmes et flux des appels

## 1. ⚠️ Ordre de démarrage obligatoire

```
1. helpops-auth    (port 2000)   ← DOIT être démarré EN PREMIER
2. helpops-server  (port 1099)   ← Se connecte à Auth au démarrage
3. helpops-client                ← Se connecte aux deux serveurs
```

**Si vous ne respectez pas cet ordre, les connexions RMI échoueront.**

---

## 2. 🔒 Hachage du mot de passe (SHA-256)

**Où :** `AuthServer.hacher(motDePasse)`  
**Quand :** À l'inscription ET à la connexion

### Algorithme

1. Prendre le mot de passe en clair (ex: `"pass123"`)
2. L'encoder en octets UTF-8
3. Appliquer **SHA-256** : produit 32 octets
4. Convertir les 32 octets en chaîne hexadécimale (64 caractères)

```
"pass123"  --SHA-256-->  "6ca13d52ca70c883e0f0bb626e92a189..."
```

✅ **À l'inscription** : on sauvegarde le hash dans `users.txt`.  
✅ **À la connexion** : on recalcule le hash du mot de passe saisi et on le compare au hash stocké.

**Le mot de passe en clair ne circule JAMAIS sur le réseau ni dans les fichiers.**

---

## 3. 📝 Inscription d'un utilisateur

```
Client                          AuthServer
  |                                 |
  |-- inscrire("bob","pass456") -->|
  |                                 | 1. Vérifier que "bob" n'existe pas déjà
  |                                 | 2. Hacher "pass456" → hash
  |                                 | 3. Créer User("bob", hash, "UTILISATEUR")
  |                                 | 4. Ajouter à la Map utilisateurs
  |                                 | 5. Écrire dans users.txt
  |<-------- true ------------------|
```

---

## 4. 🔑 Connexion et création du token

```
Client                          AuthServer
  |                                 |
  |-- connecter("bob","pass456") -->|
  |                                 | 1. Chercher "bob" dans utilisateurs
  |                                 | 2. Hacher "pass456"
  |                                 | 3. Comparer les deux hash
  |                                 | 4. Si OK : créer Token("bob")
  |                                 |    - valeur = UUID aléatoire
  |                                 |    - expiration = maintenant + 1h
  |                                 | 5. Stocker token dans tokensActifs
  |<-------- Token objet -----------|
```

Le client conserve l'objet `Token` en mémoire pour toute la session.

---

## 5. 🚨 Signalement d'un incident (appel croisé Auth ↔ Server)

```
Client          HelpOpsServer          AuthServer
  |                  |                     |
  |-- signalerIncident(tokenValeur, ...) ->|
  |                  | 1. getLoginDepuisToken(tokenValeur) -->|
  |                  |                     | 2. Vérifier token en mémoire
  |                  |                     | 3. Vérifier expiration
  |                  |<-- "bob" -----------|
  |                  | 4. Créer Incident(id, cat, titre, desc, "bob")
  |                  | 5. Ajouter à la liste incidents
  |                  | 6. Sérialiser dans incidents.dat
  |<-- Incident -----|
```

---

## 6. ✅ Vérification du token (dans le serveur)

`loginDepuisToken(tokenValeur)` appelle `auth.getLoginDepuisToken(tokenValeur)` via RMI.  
`getLoginDepuisToken` appelle d'abord `verifierToken` qui :

1. Cherche le token dans `tokensActifs` (Map en mémoire)
2. Si **absent** → retourne `null`
3. Si **présent** : vérifie que `new Date().before(token.getExpiration())`
4. Si **expiré** → supprime le token et retourne `null`
5. Si **valide** → retourne le login

---

## 7. 💾 Persistance des incidents (sérialisation Java)

**Fichier :** `incidents.dat` (binaire)

### Sauvegarde (après chaque nouvel incident)
1. Ouvrir un `ObjectOutputStream` sur le fichier
2. Écrire la `List<Incident>` entière (`writeObject`)
3. Écrire le dernier ID utilisé (`writeInt`)

### Chargement (au démarrage du serveur)
1. Ouvrir un `ObjectInputStream`
2. Lire la liste et l'ID
3. Initialiser `compteurId` à `dernierID + 1`
4. Si le fichier n'existe pas → démarrage avec liste vide et ID = 1

---

## 8. 👥 Persistance des utilisateurs (fichier texte)

**Fichier :** `users.txt` (lisible)  
**Format :** `login:hash_sha256:role` (une ligne par utilisateur)

### Chargement (au démarrage de AuthServer)
1. Lire chaque ligne
2. Découper selon `:`
3. Créer un `User` et l'ajouter dans la Map `utilisateurs`

### Sauvegarde (après chaque inscription)
1. Réécrire le fichier en entier avec tous les utilisateurs

---

## 9. 🔄 Résumé du flux complet (cas nominal)

| Ordre | Qui fait quoi |
|-------|---------------|
| 1. | **Auth démarre** : lit `users.txt`, attend des connexions (port 2000) |
| 2. | **Server démarre** : se connecte à Auth via RMI, lit `incidents.dat`, attend des connexions (port 1099) |
| 3. | **Client démarre** : se connecte aux deux serveurs via RMI |
| 4. | Client : l'utilisateur choisit "Connexion" |
| 5. | **Client → Auth** : `connecter(login, mdp)` |
| 6. | Auth vérifie hash, crée Token, retourne Token au client |
| 7. | Client affiche le menu principal |
| 8. | Client : l'utilisateur signale un incident |
| 9. | **Client → Server** : `signalerIncident(token.getValeur(), ...)` |
| 10. | **Server → Auth** : `getLoginDepuisToken(tokenValeur)` |
| 11. | Auth retourne le login (ou `null` si token invalide/expiré) |
| 12. | Server crée l'Incident, sauvegarde, retourne l'Incident au client |
13.    Client affiche la confirmation
```
