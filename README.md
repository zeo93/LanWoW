# LanWoW

App Android per consultare i dati di un personaggio di World of Warcraft:

- **Profilo** (classe, spec, item level, gilda) e **punteggio Mythic+** da [raider.io](https://raider.io)
- **Progressione raid** e migliori run M+
- **Log e parse per boss** da [WarcraftLogs](https://www.warcraftlogs.com)

## Download

**Android**: scarica l'APK dalla pagina [Releases](https://github.com/zeo93/LanWoW/releases/latest).
L'app controlla da sola gli aggiornamenti all'avvio e propone il download della nuova versione.

**iPhone/iPad e browser**: usa la web app su **<https://zeo93.github.io/LanWoW/>** —
su iOS aprila in Safari e scegli *Condividi → Aggiungi alla schermata Home* per installarla
come app. Si aggiorna da sola a ogni apertura (il codice è in `docs/`, servito da GitHub Pages;
la previsione del cutoff titolo viene rigenerata ogni giorno da una GitHub Action).

## Log di WarcraftLogs

Le credenziali API sono integrate nell'APK: i log funzionano senza configurare nulla.

## Verifica sviluppatore Android (obbligatoria dal 2027)

Google richiederà che le app installate su dispositivi Android certificati siano
registrate da uno sviluppatore verificato: dal 30/09/2026 solo in Brasile, Indonesia,
Singapore e Thailandia e solo per gli store aderenti, dal 2027 ovunque e anche per le
app installate manualmente. Dati necessari per registrare questa app sulla
[Android Developer Console](https://android.google.com/developerconsole):

- **Package name**: `com.marco.lanwow`
- **Impronta SHA-256 della chiave di firma**:
  `B6:F6:56:89:85:9F:74:A4:9E:78:40:A7:DC:CF:BF:CB:A8:A0:15:A2:6D:5E:36:33:67:B1:21:C1:89:C8:25:4A`

Senza il keystore (`app/release.keystore`) non è possibile né registrare il package
né pubblicare aggiornamenti installabili sopra l'app esistente: va conservato.

La [web app](https://zeo93.github.io/LanWoW/) non è interessata da questi requisiti.

## Build

Serve un file `secrets.properties` nella cartella del progetto (non è nel repo) con:

```
wcl.clientId=IL_TUO_CLIENT_ID
wcl.clientSecret=IL_TUO_CLIENT_SECRET
```

Poi:

```
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" gradle assembleRelease
```

L'APK firmato viene generato in `app/build/outputs/apk/release/`.
