"""Transforme le dossier lanceur/ en édition « LexOS Démon » avant la compilation.
Lancé seulement par le workflow apk-demon.yml : le dépôt reste l'édition PRO."""
import re, pathlib

def maj(chemin, fn):
    p = pathlib.Path(chemin); s = p.read_text(encoding="utf-8"); n = fn(s)
    assert n != s, f"rien changé dans {chemin}"
    p.write_text(n, encoding="utf-8")

# Paquet séparé : s'installe à côté de LexOS PRO
maj("app/build.gradle.kts", lambda s: re.sub(r'versionName = "([^"]+)"', r'versionName = "\1-demon"',
    s.replace('applicationId = "ca.lexos.lanceur"', 'applicationId = "ca.lexos.demon"')))

# Nom, et fond d'écran animé
SERVICE = '''        <service
            android:name=".DemonWallpaper"
            android:label="@string/demon_fond_nom"
            android:exported="true"
            android:permission="android.permission.BIND_WALLPAPER">
            <intent-filter>
                <action android:name="android.service.wallpaper.WallpaperService" />
            </intent-filter>
            <meta-data android:name="android.service.wallpaper" android:resource="@xml/demon_wallpaper" />
        </service>
    </application>'''
maj("app/src/main/AndroidManifest.xml", lambda s: s.replace('android:label="LexOS PRO', 'android:label="LexOS Démon')
    .replace('ca.lexos.lanceur.verrou', 'ca.lexos.demon.verrou').replace("    </application>", SERVICE, 1))

# Couleur démon par défaut (accueil + verrouillage)
for f in ["app/src/main/assets/www/index.html", "app/src/main/assets/www/verrou.html"]:
    maj(f, lambda s: s.replace("""localStorage.getItem('lexos.couleur')||'"orange"'""", """localStorage.getItem('lexos.couleur')||'"démon"'"""))

print("Édition LexOS Démon prête")
