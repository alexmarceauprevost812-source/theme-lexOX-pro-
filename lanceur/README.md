# Lanceur LexOS PRO (Android)

Écran d'accueil Android au thème LexOS PRO : fond noir, boutons orange, texte blanc / gris mat,
style terminal avec animations (démarrage `[ OK ]`, curseur qui clignote, pluie binaire, balayage d'écran,
widget `neofetch` avec la mascotte, tuiles qui « popent », éclair orange au lancement).

## Ce qu'il fait
- **Accueil** : heure, date, widget terminal (modèle du téléphone, batterie, nombre d'applis), 8 applis épinglées, barre `$ lexos --chercher…`, dock (téléphone, messages, navigateur, caméra).
- **Glisser vers le haut** (ou toucher `ls /applications`) : toutes les applis, filtre `grep`, catégories `--outils`, `--ia`, `--média`.
- **Glisser vers le bas** : volet de notifications.
- **Appui long sur une appli** : l'épingler / la retirer de l'accueil, ou ouvrir ses infos.
- **Appui long sur le fond** : changer le fond d'écran, couper les animations, réglages Android.
- **Icônes LexOS** pour les applis reconnues (Termux, Claude, NetHunter, Caméra, Galerie, Mes fichiers, Paramètres, YT Music, Notes, Chrome, Internet, Quick Share, Dossier sécurisé…). Les autres gardent leur icône dans une tuile noire.

## Nouveautés 1.2
- **Pages horizontales** : glisse à gauche/droite depuis l'accueil pour voir toutes tes applis en pages (A–Z), avec points orange et effet 3D. Bouton Accueil ou Retour = revenir à la page 1.
- **Heure animée** : quand un chiffre change, 3 caractères chinois vert lime défilent, puis le chiffre tombe en blanc.
- **Claude** : titre animé « CLAUDE » plein écran à l'ouverture, nom qui ondule sous la tuile.
- **ChatGPT** : la vraie icône de l'appli se construit (balayage + anneau vert lime) avant l'ouverture.
- **Ta photo** : appui long sur le fond (ou toucher la photo du widget) → « changer ma photo du widget » / « changer l'image de fond » / « remettre les images LexOS ».

## Installer
1. Onglet **Releases** → `lanceur-lexos-pro` → télécharge `LexOS-PRO-Lanceur.apk` sur le téléphone.
2. Ouvre-le et autorise « Installer des applis inconnues » pour ton navigateur ou Mes fichiers.
3. Appuie sur le bouton **Accueil** → choisis **LexOS PRO** → **Toujours**.
   (Ou : Paramètres → Applications → Choisir les applis par défaut → Appli d'accueil → LexOS PRO.)

Pour revenir à One UI : même menu → **One UI Home**.

## Modifier
- Interface et animations : `app/src/main/assets/www/index.html`
- Icônes : `app/src/main/assets/www/icones/` · correspondance appli → icône : objet `ICONES` dans `index.html`
- Pont Android (lister/ouvrir les applis, batterie…) : `app/src/main/java/ca/lexos/lanceur/MainActivity.kt`

## Signature
APK signé par **Alex Marceau Prevost** (certificat valide 32 ans).
La clé est gardée dans les Secrets GitHub du dépôt (`LEXOS_KEYSTORE_B64`, `LEXOS_KEYSTORE_PASS`), jamais dans le code.

Polices : Orbitron et JetBrains Mono (licence SIL OFL, dans `assets/www/polices/`).
