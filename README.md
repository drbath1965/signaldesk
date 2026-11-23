# Signal Desk

A personal finance studio: budget, bills, debt payoff, net worth, retirement planning, investments, stock buy/sell signals, and a project backlog, in one place.

It comes in two versions that share the same calculations and data format:

| | Desktop app (`desktop/`) | Web page (`web/`) |
|---|---|---|
| Runs as | `SignalDesk-<version>.jar`, Java 17+ | One HTML page, published as a claude.ai artifact |
| Your data | `SignalDesk/studio.json` in your home folder, saved automatically, with daily backups | Your claude.ai account |
| Stock prices | Downloads automatically from Yahoo Finance: pick a stock and it charts, refreshing every 5 minutes | Load a price file you download |

![Overview](docs/overview.png)

## Sections

- **Overview**: net worth trend, this month's cash flow, what needs attention, and bills due in the next two weeks.
- **Budget**: monthly income and categories grouped into needs, wants and savings, checked against the 50/30/20 guide, plus an expense log.
- **Bills**: recurring monthly, quarterly and yearly bills on a calendar. Paid bills flow into the budget.
- **Debt payoff**: avalanche or snowball plan with minimum roll-over, debt-free date, and total interest compared with paying minimums only.
- **Net worth**: accounts and assets against debts, with a monthly history.
- **Retirement**: year-by-year projection in today's dollars across traditional, Roth and taxable savings, with employer match, Social Security, 2026 IRS 401(k) limits, and a drawdown to your plan-to age.
- **Investments**: holdings, gains, and allocation against targets with rebalancing amounts.
- **Stock signals**: line, candlestick or OHLC charts over a stock's full daily history, with optional SMA, EMA 21, SMA 200, Bollinger Bands, volume, RSI and MACD; plus a rules-based trend and momentum model (SMA crossovers confirmed by MACD, RSI dip-buys, trailing stop) backtested over the last five years against buy-and-hold.
- **Backlog**: the project's own to-do board.

![Stock signals](docs/signals.png)

## Desktop app

### Run
Double-click `SignalDesk-1.1.0.jar` (the number is the version), or:

```sh
java -jar SignalDesk-1.1.0.jar
```

Requires Java 17 or newer. There are no other dependencies.

### Build
```sh
cd desktop
./build.sh        # produces desktop/SignalDesk-<version>.jar
```
The version lives in `desktop/VERSION`. Change it there and rebuild; the jar's name, its manifest and the version shown in the app all follow.

### Code layout
```
desktop/src/com/signaldesk/
  App.java          entry point: loads studio.json (or the example household) and opens the window
  json/             dependency-free JSON reader and writer
  model/            data model; JSON shape matches the web version's backup format
  calc/             budget, bills, debt, net worth, portfolio, retirement and attention-list engines
  signals/          price-file parsing and the buy/sell signal engine
  store/            file storage, daily backups, saved prices, Yahoo Finance downloads and symbol search, example data
  ui/               Swing window, theme (light and dark), widgets, editable tables, charts
  ui/views/         one class per section
desktop/res/fonts/  Inter and DejaVu Sans Mono, bundled so the app looks the same everywhere (licenses included)
desktop/test/       Parity (checks numbers), Roundtrip (save and reload), Shots (renders each section to PNG),
                    AutoPrices + mock/yahoo_mock.py (end-to-end price download against a local stand-in server),
                    HistoryTest (full download, saved file, incremental merge, re-download after a dividend),
                    ChartShots (renders the chart types)
```

### Moving data between versions
In the web version, use **Backup & data → Export backup**. In the desktop app, use **Backup & data → Restore from backup** and pick that file. The two versions do not sync automatically.

### Stock prices
Type a ticker (MSFT) or a company name (Microsoft) on the Stock signals page and pick a match. The stock's entire daily history (open, high, low, close, adjusted close and volume) downloads from Yahoo Finance's public chart service and is saved as `SignalDesk/prices/<SYMBOL>.csv` in the standard `Date,Open,High,Low,Close,Adj Close,Volume` layout, which opens in Excel. After the first download only the last month is fetched and merged in. If those days no longer match what's saved (a split or dividend changed past adjusted prices), the full history is downloaded again. While the page is open prices refresh every 5 minutes, and holdings on the Investments page update when the app opens. No account or API key is needed. If the service can't be reached, the last saved prices are shown. Yahoo's service is free but unofficial, so it can change without notice.

Charts show adjusted prices, so candles, the close line and every indicator stay on one consistent scale across splits and dividends. Chart type, range and indicators are remembered in `SignalDesk/settings.json`.

## Web version
`web/index.html` is the published page. It runs inside claude.ai, where it saves to your account.

## Not financial advice
Signal Desk is a planning and screening tool. Backtests describe the past, projections assume steady returns, and nothing here is a recommendation to buy or sell.
