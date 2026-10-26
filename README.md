# PlatipoAste

Casa d'Aste (Auction House) per **Purpur / Paper 1.21.11** basata su **valuta fisica**: niente Vault, niente economia virtuale. Si compra e si vende esclusivamente con oggetti custom (Moneta, Pila, Sacco, Moneta Liquida) i cui tassi di cambio sono configurabili.

Tutto è configurabile da `config.yml` e `messages.yml`: messaggi, GUI, slot, materiali, lore, suoni, valori delle monete e limiti.

---

## Indice

- [Caratteristiche](#caratteristiche)
- [Requisiti](#requisiti)
- [Build](#build)
- [Installazione e setup](#installazione-e-setup)
- [Comandi e permessi](#comandi-e-permessi)
- [Valuta fisica](#valuta-fisica)
- [Come funziona l'acquisto](#come-funziona-lacquisto)
- [Come funziona la vendita (100% GUI)](#come-funziona-la-vendita-100-gui)
- [Pagamento al venditore](#pagamento-al-venditore)
- [Sicurezza e anti-duping](#sicurezza-e-anti-duping)
- [Configurazione](#configurazione)
- [Messaggi](#messaggi)
- [Formato di data.yml](#formato-di-datayml)
- [Struttura del progetto](#struttura-del-progetto)
- [Limiti noti](#limiti-noti)

---

## Caratteristiche

- Valuta **fisica** a 4 tagli con tassi di cambio configurabili (default: 1 / 16 / 256 / 4.096).
- Item valuta importati con `/ahsetcurrency`, salvati in `config.yml` con NBT, CustomModelData, nome e lore, e riconosciuti con `isSimilar`.
- Scansione del saldo nell'inventario **e dentro le Shulker Box** presenti nell'inventario.
- **Resto ottimizzato**: restituito col minor numero di oggetti possibile (Moneta Liquida → Sacco → Pila → Moneta), nell'inventario o nelle Shulker Box.
- Vendita **interamente via GUI**: nessun comando testuale per mettere in vendita.
- Menu principale con **paginazione**, filtro **I Miei Oggetti** e pulsante **Metti in Vendita**.
- Transazioni **atomiche** con controlli anti-duplicazione e anti race-condition.
- Persistenza su `data.yml`: le aste sopravvivono al riavvio.
- Testi con **codici legacy** (`&a`, `&#RRGGBB`) e **MiniMessage**.
- Suoni di click, cambio pagina, successo ed errore, personalizzabili o disattivabili.

## Requisiti

- Server **Purpur** o **Paper** 1.21.11
- **Java 21**
- Maven 3.8+ (solo per compilare)

## Build

```bash
mvn clean package
```

Il jar viene generato in `target/PlatipoAste.jar`.

## Installazione e setup

1. Copia `PlatipoAste.jar` in `plugins/` e avvia il server: vengono creati `config.yml`, `messages.yml` e `data.yml`.
2. Per ogni valuta, tieni in mano l'oggetto reale (nome, lore e CustomModelData compresi) ed esegui:
   ```
   /ahsetcurrency moneta
   /ahsetcurrency pila
   /ahsetcurrency sacco
   /ahsetcurrency liquida
   ```
   Finché non lo fai, il plugin usa item di prova (pepita, lingotto e blocco d'oro, stella del Nether) e lo segnala in console. Usali solo per test.
3. Modifica `config.yml` / `messages.yml` a piacere e applica con `/ah reload`.
4. I giocatori aprono la casa d'aste con `/ah`.

> `/ahsetcurrency` richiama `saveConfig()`: `config.yml` viene riscritto e alcuni commenti potrebbero andare persi. Tieni una copia del file.

## Comandi e permessi

| Comando | Descrizione | Permesso |
|---|---|---|
| `/ah` (alias `/auctionhouse`, `/aste`) | Apre il menu principale | `platipoaste.use` |
| `/ah reload` | Ricarica `config.yml` e `messages.yml` | `platipoaste.admin` |
| `/ahsetcurrency <moneta\|pila\|sacco\|liquida>` | Salva l'item in mano come valuta | `platipoaste.admin` |

| Permesso | Default | Descrizione |
|---|---|---|
| `platipoaste.use` | tutti | Aprire il menu e comprare |
| `platipoaste.sell` | tutti | Mettere oggetti in vendita |
| `platipoaste.admin` | op | Comandi amministrativi |

## Valuta fisica

| Valuta | ID | Valore di default |
|---|---|---|
| Moneta | `moneta` | 1 |
| Pila di Monete | `pila` | 16 |
| Sacco di Monete | `sacco` | 256 (16 pile) |
| Moneta Liquida | `liquida` | 4.096 (16 sacchi) |

Valori e nomi sono modificabili in `config.yml`, sezione `currency`:

```yaml
currency:
  pila:
    name: "Pila di Monete"
    value: 16
    item: ...            # scritto da /ahsetcurrency
    default-item: ...    # item di prova usato finché "item" non esiste
```

Il riconoscimento usa `ItemStack#isSimilar`, che ignora la quantità e confronta tutto il resto.

Avvisa in console se non esiste nessun taglio di valore 1, perché in quel caso il resto potrebbe non essere rappresentabile.

## Come funziona l'acquisto

Quando un giocatore clicca un'inserzione:

1. **Blocco:** l'inserzione viene bloccata, quindi un secondo acquirente riceve `purchase-busy`.
2. **Verifica:** l'inserzione viene riletta da `data.yml`. Se non esiste più, l'acquisto fallisce con `purchase-gone`.
3. **Saldo:** viene calcolato il saldo totale tra inventario e Shulker Box.
4. **Simulazione:** tutto viene simulato su **copie** dell'inventario. Si prelevano le monete partendo dai tagli piccoli, poi si calcola il resto e si controlla che ci sia spazio per l'item e per il resto.
5. **Persistenza:** se la simulazione riesce, l'inserzione viene rimossa, il credito del venditore viene registrato e `data.yml` viene salvato su disco.
6. **Commit:** solo a questo punto viene modificato l'inventario reale.

Se manca lo spazio o i fondi, **non viene prelevata nessuna moneta**.

**Esempio:** prezzo 17 (1 Pila + 1 Moneta), pagando con 1 Sacco: viene prelevato il Sacco e restituito un resto di 239 monete (14 Pile + 15 Monete).

> L'oggetto comprato va sempre nell'inventario normale. Il resto può andare anche dentro le Shulker Box.

## Come funziona la vendita (100% GUI)

1. Nel menu `/ah` clicca **Metti in Vendita**.
2. **Selezione:** clicca l'oggetto dal tuo inventario (in basso). Click sinistro = tutta la pila, click destro = un solo pezzo. Poi **Conferma selezione**.
3. **Prezzo:** per ogni taglio ci sono i pulsanti `+` e `−` (con shift-click il passo è più grande, `gui.price.shift-step`). Il prezzo totale è la somma `quantità × valore` di ogni taglio.
4. **Conferma Inserzione:** l'oggetto viene tolto dall'inventario e pubblicato sul mercato.

Prima di pubblicare, il plugin ricontrolla che l'oggetto sia ancora nello slot scelto e che prezzo e limiti siano validi. Se la pubblicazione fallisce, l'oggetto viene restituito.

Clic su una **propria** inserzione dal menu principale = ritiro dell'oggetto (serve spazio nell'inventario).

## Pagamento al venditore

Le monete di una vendita **non vengono consegnate direttamente**: vengono registrate come credito a nome del venditore in `data.yml` (`earnings.<uuid>`). Il venditore le ritira col pulsante **Riscuoti Guadagni** nel menu `/ah`.

- Il venditore online riceve il messaggio `seller-notify`; se è offline riceve `earnings-pending` al login.
- Le monete consegnate sono già divise nei tagli più grandi possibili.
- Se l'inventario è pieno, vengono consegnate solo le monete che ci stanno e il resto resta in sospeso (`claim-partial`).

## Sicurezza e anti-duping

- **Lock per inserzione** (`ConcurrentHashMap`) e **lock globale** (`ReentrantLock`) sulle operazioni di dati.
- Solo un acquirente completa l'acquisto; gli altri ricevono un messaggio configurabile.
- L'inserzione è riletta da `data.yml` prima di scalare monete o consegnare item; prezzo e item usati sono quelli salvati, **non** quelli mostrati nella GUI.
- Simulazione completa prima di toccare l'inventario; commit solo se tutto è valido.
- Salvataggio su disco con scrittura **atomica** (file temporaneo + move) **prima** del commit dell'inventario: in caso di errore di salvataggio la transazione viene annullata.
- Le GUI annullano qualsiasi spostamento di item (click, drag, shift-click, tasti numerici, doppio click, drop, scambio con la mano secondaria).
- Cooldown anti doppio-click (`settings.click-cooldown-ms`).
- Blacklist di materiali non vendibili e, di default, divieto di vendere gli item valuta.

## Configurazione

Tutti i testi supportano **codici legacy** (`&a`, `&l`, `&#RRGGBB`) e **MiniMessage** (`<red>`, `<gradient:...>`). Gli item nelle GUI usano lo stesso sistema.

### `settings`

| Chiave | Default | Descrizione |
|---|---|---|
| `max-listings-per-player` | `10` | Inserzioni massime a testa (`0` = illimitato) |
| `min-price` / `max-price` | `1` / `100000000` | Limiti di prezzo (in monete) |
| `max-per-tier` | `999` | Massimo di pezzi per taglio nella GUI prezzo |
| `use-shulker-boxes` | `true` | Scansiona e deposita anche nelle Shulker Box |
| `allow-selling-currency` | `false` | Permette di vendere gli item valuta |
| `click-cooldown-ms` | `150` | Anti doppio-click |
| `blacklist-materials` | lista | Materiali non vendibili |

### `format`

Formato data (`date`), voci della scomposizione monete (`breakdown-entry`, `breakdown-separator`, `breakdown-empty`) e stati del filtro (`state-on`, `state-off`).

### `sounds`

Quattro suoni: `click`, `page`, `success`, `error`. Ognuno ha `enabled`, `sound`, `volume`, `pitch`. Il suono si indica con la chiave namespaced di Minecraft (es. `ui.button.click`, `entity.player.levelup`).

```yaml
sounds:
  success: { enabled: true, sound: "entity.player.levelup", volume: 0.8, pitch: 1.2 }
```

### `gui`

Tre GUI configurabili: `main`, `select`, `price`. Per ognuna si impostano titolo, `rows`, `filler` e i pulsanti. Ogni pulsante supporta:

| Campo | Descrizione |
|---|---|
| `slot` | Posizione nell'inventario |
| `material` | Materiale |
| `name` | Nome visualizzato |
| `lore` | Lista di righe |
| `amount` | Quantità (1-64) |
| `custom-model-data` | CustomModelData |
| `glow` | Effetto incantato |

**Menu principale (`gui.main`):**

- `auction-slots`: slot usati per le inserzioni (default 0-44). Non includere slot dei pulsanti.
- `auction-lore` / `own-lore`: lore aggiunta agli item altrui e ai propri.
- `hide-unavailable-nav`: se `true`, nasconde "precedente/successiva" quando non servono.
- Pulsanti: `previous`, `next`, `sell`, `my-items`, `claim`.

**Selezione (`gui.select`):** `preview` (con `empty` e `selected-lore`) e pulsanti `confirm`, `back`.

**Prezzo (`gui.price`):** `preview`, `add-button` / `remove-button` (template), `display-lore`, `shift-step`, `tiers.<id>` (`add-slot`, `display-slot`, `remove-slot`) e pulsanti `confirm`, `back`, `reset`, `cancel`.

### Placeholder

| Dove | Placeholder |
|---|---|
| Titolo menu principale | `{page}` `{pages}` `{total}` `{pending}` |
| Pulsanti menu principale | `{prev}` `{next}` `{state}` `{pending}` |
| Lore inserzioni | `{seller}` `{price}` `{price_breakdown}` `{date}` `{amount}` |
| Selezione | `{amount}` `{item}` |
| Prezzo | `{price}` `{breakdown}` `{min}` `{max}` |
| Pulsanti `+`/`−` e display | `{currency}` `{value}` `{count}` `{subtotal}` `{step}` `{shift_step}` |

## Messaggi

Tutti i messaggi sono in `messages.yml`. `{prefix}` viene sostituito automaticamente. Una stringa vuota (`""`) disattiva il messaggio.

| Gruppo | Chiavi |
|---|---|
| Generali | `no-permission`, `players-only`, `reload-done` |
| Valuta | `setcurrency-usage`, `setcurrency-invalid`, `setcurrency-no-item`, `setcurrency-success` |
| Acquisto | `purchase-success`, `purchase-no-funds`, `purchase-gone`, `purchase-busy`, `purchase-no-space`, `purchase-error`, `seller-notify` |
| Ritiro | `cancel-success`, `cancel-no-space`, `cancel-not-owner` |
| Vendita | `sell-no-permission`, `sell-select-first`, `sell-invalid-item`, `sell-blacklisted`, `sell-currency-forbidden`, `sell-selected`, `sell-price-low`, `sell-price-high`, `sell-changed`, `sell-limit`, `sell-error`, `sell-success` |
| Guadagni | `claim-success`, `claim-partial`, `claim-none`, `claim-no-space`, `earnings-pending` |
| GUI | `tier-max` |

## Formato di data.yml

```yaml
auctions:
  <uuid-inserzione>:
    seller: <uuid-venditore>
    seller-name: Nome
    price: 17
    created: 1760000000000
    item: ...            # ItemStack serializzato
earnings:
  <uuid-venditore>: 5000   # monete in attesa di riscossione
```

## Struttura del progetto

```
PlatipoAste/
├── pom.xml
└── src/main/
    ├── java/it/platipo/aste/
    │   ├── PlatipoAste.java      # classe principale
    │   ├── AhCommand.java        # /ah, /ah reload, /ahsetcurrency
    │   ├── GuiManager.java       # tutte le GUI e gli eventi
    │   ├── GuiUtil.java          # costruzione item/GUI da config
    │   ├── AuctionManager.java   # aste, lock, persistenza, acquisti
    │   ├── Auction.java          # record dell'inserzione
    │   ├── CurrencyManager.java  # tagli, valori, isSimilar, scomposizione
    │   ├── Wallet.java           # simulazione transazionale dell'inventario
    │   ├── Messages.java         # messages.yml
    │   ├── SoundManager.java     # suoni configurabili
    │   └── Text.java             # legacy + MiniMessage
    └── resources/
        ├── plugin.yml
        ├── config.yml
        └── messages.yml
```

## Limiti noti

- **Nessuna scadenza** delle inserzioni.
- **Nessuna tassa** o commissione sulle vendite.
- **Algoritmo di pagamento:** parte dai tagli piccoli. In alcuni casi (es. 1 Pila + 1 Sacco per un prezzo di 17) il valore totale resta corretto, ma il resto può contenere più pezzi di quanti servirebbero spendendo solo il taglio più grande.
- **Salvataggio sincrono:** `data.yml` viene scritto in modo sincrono a ogni modifica. Va bene per molte decine di inserzioni; per volumi molto grandi conviene passare a un database.
- Il plugin funziona solo con l'**inventario principale** (36 slot) e le Shulker Box al suo interno: armatura e mano secondaria non sono incluse.

## Licenza

MIT
