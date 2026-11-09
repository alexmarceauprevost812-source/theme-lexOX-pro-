#!/data/data/com.termux/files/usr/bin/env bash
# =====================================================================
#  setup-terminal-lexos.sh — habille TERMUX aux couleurs de LexOS.
#
#  À COLLER UNE FOIS dans Termux (ou : bash setup-terminal-lexos.sh).
#  Idempotent : tu peux le relancer, il remplace sa propre configuration.
#
#  Ce que ça fait :
#   1. ~/.termux/colors.properties : fond noir pur, texte blanc, curseur
#      orange #ff6a1a (ton ancien fichier est sauvegardé une fois), puis
#      termux-reload-settings.
#   2. ~/.lexos-terminal/lexos.sh : les commandes
#        lexos          menu LexOS (logo orange #E06020)
#        lexos help     l'aide
#        lexos outils   sous-menu « Outils d'audit »
#        lexfetch       infos du système
#   3. un bloc « LexOS Terminal » dans ~/.bashrc qui charge ce fichier
#      (rien n'est lancé à l'ouverture, sauf une ligne d'indice).
#
#  ⚠️ Si LexOS Pro (l'APK complet) est déjà ton Termux, le script s'arrête :
#     il a déjà sa propre commande « lexos ». Pour forcer : --force
#  ⚠️ Sans root. Usage légal seulement (tes appareils / ton réseau).
# =====================================================================

# Tout est dans UNE fonction dont le corps est un sous-shell ( … ) :
# coller ce script dans Termux ne ferme jamais ta session (un « exit »
# ne sort que du sous-shell) et ne laisse aucun réglage (set -u…) derrière.
setup_terminal_lexos() (
    set -u
    O=$'\e[38;2;224;96;32m'; G=$'\e[1;38;2;118;185;0m'; RED=$'\e[1;31m'; D=$'\e[2m'; R=$'\e[0m'
    FORCE=0
    for a in "$@"; do [ "$a" = "--force" ] && FORCE=1; done

    # --- On est bien dans Termux ? ---
    case "${PREFIX:-}" in
        */com.termux/*) ;;
        *) echo "${RED}Ce script est fait pour Termux (com.termux).${R}"; exit 1 ;;
    esac

    # --- LexOS Pro (APK) déjà là ? Il a sa propre commande « lexos » : on ne la remplace pas. ---
    if [ -f "$HOME/.lexos/.version" ] && [ "$FORCE" -eq 0 ]; then
        echo "${O}❯_ LexOS${R} LexOS Pro (l'APK complet) est déjà installé ici."
        echo "${D}Il a déjà sa commande « lexos » : rien à faire. (Pour forcer quand même : --force)${R}"
        exit 0
    fi

    echo "${O}❯_ LexOS Terminal${R} ${D}— configuration de Termux…${R}"

    # --- 1) Couleurs ---
    mkdir -p "$HOME/.termux"
    CP="$HOME/.termux/colors.properties"
    if [ -f "$CP" ] && [ ! -f "$CP.bak-lexos" ] && ! grep -q 'LexOS Terminal' "$CP" 2>/dev/null; then
        cp "$CP" "$CP.bak-lexos" && echo "${D}  ancien thème sauvegardé : colors.properties.bak-lexos${R}"
    fi
    cat > "$CP" <<'COLORS'
# LexOS Terminal — fond noir pur, texte blanc, curseur orange
background=#000000
foreground=#ffffff
cursor=#ff6a1a
COLORS
    echo "${G}  ✅ couleurs : fond noir, texte blanc, curseur orange${R}"
    if command -v termux-reload-settings >/dev/null 2>&1; then
        termux-reload-settings 2>/dev/null && echo "${D}  réglages rechargés${R}"
    else
        echo "${D}  (ferme et rouvre Termux pour voir les couleurs)${R}"
    fi

    # --- 2) Les commandes LexOS ---
    mkdir -p "$HOME/.lexos-terminal"
    cat > "$HOME/.lexos-terminal/lexos.sh" <<'LEXOS_SH'
# ~/.lexos-terminal/lexos.sh — commandes LexOS pour Termux (chargé par ~/.bashrc)
# Shell interactif seulement ; et on laisse la place à LexOS Pro (APK) s'il est là.
[[ $- == *i* ]] || return 0
[ -f "$HOME/.lexos/.version" ] && return 0

__LX_O=$'\e[38;2;224;96;32m'      # orange LexOS  #E06020 (logo)
__LX_A=$'\e[1;38;2;255;106;26m'   # accent        #FF6A1A (comme le curseur)
__LX_G=$'\e[1;38;2;118;185;0m'    # vert lime (succès)
__LX_RED=$'\e[1;31m'; __LX_D=$'\e[2m'; __LX_R=$'\e[0m'

# ---- Logo LEXOS (orange #E06020) ----
__lx_logo() {
    local -a L=("█    " "█    " "█    " "█    " "█████") \
             E=("█████" "█    " "████ " "█    " "█████") \
             X=("█   █" " █ █ " "  █  " " █ █ " "█   █") \
             O=(" ███ " "█   █" "█   █" "█   █" " ███ ") \
             S=(" ████" "█    " " ███ " "    █" "████ ")
    local i
    for i in 0 1 2 3 4; do
        printf '%s%s  %s  %s  %s  %s%s\n' "$__LX_O" "${L[i]}" "${E[i]}" "${X[i]}" "${O[i]}" "${S[i]}" "$__LX_R"
    done
}

# ---- lexfetch : infos du système ----
lexfetch() {
    local model android kernel arch cpu ram stock pk up ver cols
    model=$(getprop ro.product.model 2>/dev/null);        [ -n "$model" ]   || model="?"
    android=$(getprop ro.build.version.release 2>/dev/null); [ -n "$android" ] || android="?"
    kernel=$(uname -r 2>/dev/null);                       [ -n "$kernel" ]  || kernel="?"
    arch=$(uname -m 2>/dev/null);                         [ -n "$arch" ]    || arch="?"
    cpu=$(nproc 2>/dev/null);                             [ -n "$cpu" ]     || cpu="?"
    ram=$(awk '/^MemTotal:/{t=$2} /^MemAvailable:/{a=$2} END{ if(t>0) printf "%d / %d Mo", (t-a)/1024, t/1024 }' /proc/meminfo 2>/dev/null)
    [ -n "$ram" ] || ram="?"
    stock=$(df -h "$HOME" 2>/dev/null | awk 'NR==2{print $3" / "$2" ("$5")"}'); [ -n "$stock" ] || stock="?"
    pk=$(dpkg-query -f '.\n' -W 2>/dev/null | wc -l | tr -d ' ');  [ -n "$pk" ] || pk="?"
    up=$(uptime -p 2>/dev/null | sed 's/^up //');         [ -n "$up" ]      || up="?"
    ver="${TERMUX_VERSION:-?}"
    cols="$(tput cols 2>/dev/null || echo '?')x$(tput lines 2>/dev/null || echo '?')"
    echo
    __lx_logo
    printf '%s  Terminal LexOS · Termux%s\n\n' "$__LX_D" "$__LX_R"
    local k v
    while IFS='|' read -r k v; do
        printf '  %s%-10s%s %s\n' "$__LX_A" "$k" "$__LX_R" "$v"
    done <<EOF
appareil|$model
android|$android
noyau|$kernel ($arch)
cpu|$cpu cœurs
mémoire|$ram
stockage|$stock
paquets|$pk
termux|$ver
écran|$cols
actif|$up
EOF
    echo
}

# ---- Aide ----
__lx_help() {
    printf '%s❯_ lexos%s — commandes du terminal LexOS\n\n' "$__LX_O" "$__LX_R"
    printf '  %slexos%s           menu LexOS\n'                       "$__LX_A" "$__LX_R"
    printf '  %slexos help%s      cette aide\n'                        "$__LX_A" "$__LX_R"
    printf '  %slexos outils%s    sous-menu « Outils d'"'"'audit »\n'  "$__LX_A" "$__LX_R"
    printf '  %slexfetch%s        infos du système (aussi : lexos fetch)\n' "$__LX_A" "$__LX_R"
    printf '\n%sSans root : les scans nmap utilisent le mode connexion (-sT).\n' "$__LX_D"
    printf 'Usage légal seulement : tes appareils / ton réseau, ou avec une autorisation écrite.%s\n' "$__LX_R"
}

# ---- Aides pour les outils d'audit ----
__lx_cible_valide() {   # lettres, chiffres, . : / _ - ; ne commence pas par « - »
    case "$1" in ""|-*|*[!A-Za-z0-9._:/-]*) return 1 ;; esac
    return 0
}
__lx_confirme() {       # $1 = cible
    printf '%s⚠️  Usage légal seulement : tes propres appareils / ton réseau, ou une autorisation écrite.%s\n' "$__LX_RED" "$__LX_R"
    printf 'La cible « %s » m'"'"'appartient, ou j'"'"'ai l'"'"'autorisation ? [o/N] ' "$1"
    local r=""; read -r r
    case "$r" in o|O|oui|OUI|y|Y) return 0 ;; *) printf '%sAnnulé.%s\n' "$__LX_D" "$__LX_R"; return 1 ;; esac
}
__lx_besoin() {         # $1 = commande, $2 = paquet pkg ; propose d'installer si absent
    command -v "$1" >/dev/null 2>&1 && return 0
    printf '%s« %s » n'"'"'est pas installé.%s Installer (pkg install %s) ? [O/n] ' "$__LX_RED" "$1" "$__LX_R" "$2"
    local r=""; read -r r
    case "$r" in ""|o|O|y|Y) pkg install -y "$2" ;; *) return 1 ;; esac
    command -v "$1" >/dev/null 2>&1
}
__lx_reseau() {         # devine le réseau local /24 (sans root ; peut échouer sur Android récent)
    local a
    a=$(ip -4 addr show 2>/dev/null | awk '/inet /{print $2}' | cut -d/ -f1 | grep -vE '^127\.' | head -n1)
    [ -z "$a" ] && a=$(ifconfig 2>/dev/null | awk '/inet /{print $2}' | grep -vE '^127\.' | head -n1)
    [ -n "$a" ] && printf '%s.0/24' "${a%.*}"
}

# ---- Sous-menu : Outils d'audit ----
__lx_outils() {
    local c cible url net
    while :; do
        echo
        printf '%s❯_ Outils d'"'"'audit%s  %s(sans root · usage légal seulement)%s\n\n' "$__LX_O" "$__LX_R" "$__LX_D" "$__LX_R"
        printf '   %s1%s  🔎 Voir les appareils de mon Wi-Fi          %s(nmap)%s\n'         "$__LX_A" "$__LX_R" "$__LX_D" "$__LX_R"
        printf '   %s2%s  🔌 Scanner les ports d'"'"'une de mes machines  %s(nmap -sT -sV)%s\n' "$__LX_A" "$__LX_R" "$__LX_D" "$__LX_R"
        printf '   %s3%s  🌐 Chercher le DNS d'"'"'un nom                %s(dig)%s\n'          "$__LX_A" "$__LX_R" "$__LX_D" "$__LX_R"
        printf '   %s4%s  🛣️  Chemin réseau vers une machine           %s(traceroute)%s\n'   "$__LX_A" "$__LX_R" "$__LX_D" "$__LX_R"
        printf '   %s5%s  💉 Tester MON site web                       %s(sqlmap, pip)%s\n'  "$__LX_A" "$__LX_R" "$__LX_D" "$__LX_R"
        printf '   %s6%s  📦 Installer les outils de base              %s(nmap, dig, nc, traceroute)%s\n' "$__LX_A" "$__LX_R" "$__LX_D" "$__LX_R"
        printf '   %s0%s  ⬅️  Retour\n' "$__LX_A" "$__LX_R"
        printf '\n%soutils ❯%s ' "$__LX_A" "$__LX_R"
        read -r c || return 0
        case "$c" in
            1)  __lx_besoin nmap nmap || continue
                net=$(__lx_reseau)
                printf 'Réseau à scanner %s[%s]%s ❯ ' "$__LX_D" "${net:-ex: 192.168.1.0/24}" "$__LX_R"
                read -r cible; [ -z "$cible" ] && cible="$net"
                __lx_cible_valide "$cible" || { printf '%sRéseau invalide.%s\n' "$__LX_RED" "$__LX_R"; continue; }
                __lx_confirme "$cible" && nmap -sn -- "$cible" ;;
            2)  __lx_besoin nmap nmap || continue
                printf 'Machine à scanner (adresse ou nom) ❯ '; read -r cible
                __lx_cible_valide "$cible" || { printf '%sCible invalide.%s\n' "$__LX_RED" "$__LX_R"; continue; }
                __lx_confirme "$cible" && nmap -sT -sV --open -- "$cible" ;;
            3)  __lx_besoin dig dnsutils || continue
                printf 'Nom à chercher (ex: exemple.com) ❯ '; read -r cible
                __lx_cible_valide "$cible" || { printf '%sNom invalide.%s\n' "$__LX_RED" "$__LX_R"; continue; }
                dig +short "$cible" ;;
            4)  __lx_besoin traceroute traceroute || continue
                printf 'Machine (adresse ou nom) ❯ '; read -r cible
                __lx_cible_valide "$cible" || { printf '%sCible invalide.%s\n' "$__LX_RED" "$__LX_R"; continue; }
                traceroute "$cible" ;;
            5)  if ! command -v sqlmap >/dev/null 2>&1; then
                    printf '%ssqlmap n'"'"'est pas dans les dépôts Termux : il s'"'"'installe avec pip.%s Installer ? [O/n] ' "$__LX_D" "$__LX_R"
                    read -r c; case "$c" in ""|o|O|y|Y) ;; *) continue ;; esac
                    command -v python >/dev/null 2>&1 || pkg install -y python
                    pip install sqlmap || { printf '%sÉchec de pip install sqlmap.%s\n' "$__LX_RED" "$__LX_R"; continue; }
                fi
                command -v sqlmap >/dev/null 2>&1 || { printf '%sCommande sqlmap introuvable après installation.%s\n' "$__LX_RED" "$__LX_R"; continue; }
                printf 'Adresse de TON site (ex: http://192.168.1.20/page.php?id=1) ❯ '; read -r url
                case "$url" in http://*|https://*) ;; *) printf '%sIl faut une adresse http:// ou https://%s\n' "$__LX_RED" "$__LX_R"; continue ;; esac
                case "$url" in *[[:space:]\;\|\&\`\$\<\>\"\']*) printf '%sCaractères non permis dans l'"'"'adresse.%s\n' "$__LX_RED" "$__LX_R"; continue ;; esac
                __lx_confirme "$url" && sqlmap -u "$url" ;;
            6)  printf 'Installer : nmap dnsutils netcat-openbsd traceroute ? [O/n] '; read -r c
                case "$c" in ""|o|O|y|Y) pkg install -y nmap dnsutils netcat-openbsd traceroute ;; esac ;;
            0|q|"") return 0 ;;
            *) printf '%sChoix inconnu.%s\n' "$__LX_RED" "$__LX_R" ;;
        esac
    done
}

# ---- Menu principal ----
__lx_menu() {
    local c
    while :; do
        echo
        __lx_logo
        printf '%s  Terminal LexOS · Termux%s\n\n' "$__LX_D" "$__LX_R"
        printf '   %s1%s  🖥️  Infos du système   %s(lexfetch)%s\n' "$__LX_A" "$__LX_R" "$__LX_D" "$__LX_R"
        printf '   %s2%s  🛡️  Outils d'"'"'audit\n'               "$__LX_A" "$__LX_R"
        printf '   %s3%s  ❓ Aide\n'                              "$__LX_A" "$__LX_R"
        printf '   %s0%s  ⬅️  Quitter le menu\n'                  "$__LX_A" "$__LX_R"
        printf '\n%slexos ❯%s ' "$__LX_A" "$__LX_R"
        read -r c || return 0
        case "$c" in
            1) lexfetch ;;
            2) __lx_outils ;;
            3) __lx_help ;;
            0|q|"") return 0 ;;
            *) printf '%sChoix inconnu.%s\n' "$__LX_RED" "$__LX_R" ;;
        esac
    done
}

lexos() {
    case "${1:-menu}" in
        menu|"")           __lx_menu ;;
        help|aide|-h|--help) __lx_help ;;
        fetch|info)        lexfetch ;;
        outils|audit)      __lx_outils ;;
        *) printf 'Commande inconnue : %s\n\n' "$1" >&2; __lx_help >&2; return 1 ;;
    esac
}

# Une seule ligne d'indice à l'ouverture (le menu ne se lance pas tout seul).
printf '%s❯_ LexOS Terminal%s %s· tape%s %slexos%s\n' "$__LX_O" "$__LX_R" "$__LX_D" "$__LX_R" "$__LX_A" "$__LX_R"
LEXOS_SH
    echo "${G}  ✅ commandes : lexos · lexos help · lexos outils · lexfetch${R}"

    # --- 3) ~/.bashrc : un bloc géré (remplacé à chaque exécution, jamais dupliqué) ---
    BRC="$HOME/.bashrc"; touch "$BRC"
    if [ ! -f "$BRC.bak-lexos" ] && ! grep -q '^# >>> LexOS Terminal >>>' "$BRC"; then
        cp "$BRC" "$BRC.bak-lexos" 2>/dev/null
    fi
    if grep -q '^# >>> LexOS Terminal >>>' "$BRC"; then
        awk '/^# >>> LexOS Terminal >>>/{s=1} !s{print} /^# <<< LexOS Terminal <<</{s=0}' "$BRC" > "$BRC.tmp-lexos" \
            && mv "$BRC.tmp-lexos" "$BRC"
    fi
    cat >> "$BRC" <<'BLOC'
# >>> LexOS Terminal >>>
[ -f "$HOME/.lexos-terminal/lexos.sh" ] && . "$HOME/.lexos-terminal/lexos.sh"
# <<< LexOS Terminal <<<
BLOC
    echo "${G}  ✅ ~/.bashrc : bloc « LexOS Terminal » installé${R}"

    echo
    echo "${O}Terminé.${R} Ouvre une nouvelle session Termux (ou tape : ${O}source ~/.bashrc${R}), puis : ${O}lexos${R}"
)

setup_terminal_lexos "$@"; __lx_rc=$?
unset -f setup_terminal_lexos
# Lancé comme un script : on rend le vrai code de sortie (0 = ok, 1 = refusé).
# Collé dans Termux ou lu avec « source » : on ne quitte JAMAIS la session et on
# ne laisse aucune variable derrière.
if [ -n "${BASH_SOURCE[0]:-}" ] && [ "${BASH_SOURCE[0]}" = "$0" ]; then
    exit "$__lx_rc"
fi
unset __lx_rc
