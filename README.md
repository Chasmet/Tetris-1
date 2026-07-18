# Vidéo Pur 4K

Application Android indépendante qui améliore une vidéo entièrement sur le téléphone, sans serveur et sans connexion après installation.

## Fonctions de la V1

- sélection d'une vidéo locale ;
- prévisualisation de l'original et du résultat ;
- trois niveaux de traitement ;
- lissage léger du bruit numérique ;
- correction du contraste, de la luminosité et des couleurs ;
- agrandissement Lanczos de haute qualité ;
- export 720p, 1080p, 2K ou 4K ;
- conservation de l'audio ;
- enregistrement dans `Films/Video Pur 4K` ;
- partage direct depuis l'application ;
- aucun compte, aucune publicité, aucune API et aucun envoi en ligne.

## Technique

- Java 17 ;
- Android 6 minimum ;
- Jetpack Media3 Transformer 1.10.1 ;
- accélération MediaCodec et OpenGL du téléphone ;
- sortie MP4 H.264 + AAC.

## Limites réelles

La V1 améliore visuellement la vidéo mais ne reconstruit pas encore des détails absents avec un grand modèle d'intelligence artificielle. Une vidéo très floue ne deviendra donc pas identique à une vraie captation 4K. Le traitement 4K dépend aussi des capacités de l'encodeur matériel du téléphone.

## APK

Chaque modification lance automatiquement GitHub Actions. L'APK de test se trouve dans l'artefact `VideoPur4K-debug-apk` du dernier workflow réussi.
