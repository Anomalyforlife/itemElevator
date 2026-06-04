# ItemElevators

A Paper plugin that lets players automatically move items upward between two vertically aligned chests. No conductor blocks required — just place two special elevator chests one above the other and link them with a right-click.

**Author:** Anomalyforlife  
**API:** Paper 1.21.x  
**Soft dependency:** Vault (optional, for economy features)

---

## How it works

1. An admin gives players special elevator chest items via `/ie give`.
2. The player places two elevator chests vertically aligned (same X/Z), within the configured `max-distance`.
3. Right-clicking the lower chest links the pair and opens the upgrade GUI.
4. Items in the bottom chest are automatically transferred to the top chest at regular intervals.

### Chaining

Elevator pairs can be chained to move items over any height. If `max-distance` is 10, placing chests at Y=0, Y=8, and Y=16 creates two linked pairs (0→8 and 8→16). Items flow upward through the chain automatically.

When upgrading a chest in a chain, **all elevators in that chain are upgraded together** and the cost is multiplied by the number of pairs in the chain.

---

## Setup

1. Drop `ItemElevators.jar` into your `plugins/` folder.
2. Start the server — `config.yml` and language files are generated automatically.
3. Give elevator chests to players: `/ie give <player> [amount]`
4. (Optional) Install [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin to enable creation costs and upgrade costs.

---

## Usage

### Getting an elevator chest

Elevator chests are special items — regular chests from your inventory cannot be used as elevators. An admin must give them:

```
/ie give <player> [amount]
```

### Placing and linking

1. Place one elevator chest at the bottom position.
2. Place another elevator chest directly above (same X/Z), within `max-distance` blocks.
3. Right-click the **bottom** chest to link the pair. A confirmation message is shown.

### Opening the GUI

Right-click any elevator chest to open its GUI. The GUI shows:

- The current contents of that specific chest (read-only view).
- An upgrade button in the bottom-right corner.

> **To manage items directly**, sneak (shift) and right-click the chest. This opens the physical chest inventory normally.

### Removing an elevator

Break either chest — it drops as a special elevator chest item carrying its current upgrade level. The upgrade is not lost.

Alternatively, look at a chest and use `/ie remove`.

---

## Commands

| Command | Description | Permission |
|---|---|---|
| `/ie give <player> [amount]` | Give elevator chest items | `itemelevators.give` |
| `/ie reload` | Reload configuration and language files | `itemelevators.reload` |
| `/ie list` | List all active elevator pairs | `itemelevators.list` |
| `/ie remove` | Remove the elevator you are looking at | `itemelevators.remove` |

Aliases: `/elevator`, `/ie`

---

## Permissions

| Permission | Description | Default |
|---|---|---|
| `itemelevators.give` | Give elevator chest items | op |
| `itemelevators.create` | Link elevator pairs by right-clicking | true |
| `itemelevators.bypass-cost` | Skip the economy creation cost | op |
| `itemelevators.reload` | Reload the plugin | op |
| `itemelevators.list` | List all elevators | op |
| `itemelevators.remove` | Remove an elevator | op |

---

## Configuration (`config.yml`)

```yaml
# Ticks between each item transfer. 20 ticks = 1 second.
transfer-interval: 10

# Maximum vertical distance (in blocks) between two linked chests.
# Increase this to allow longer single jumps; chain pairs for unlimited height.
max-distance: 10

# Language file: en or it
language: en

economy:
  enabled: false
  creation-cost: 100.0   # Cost to link a new pair (0 = free)

upgrades:
  enabled: true
  vault-required: true   # Require Vault to purchase upgrades
  max-level: 10
  levels:
    1:
      cost: 0
      items-per-transfer: 1
    # ... up to level 10
```

---

## Upgrade system

Each elevator pair has an upgrade level (1–10 by default) that controls how many items are transferred per cycle.

| Level | Items/transfer | Default cost |
|---|---|---|
| 1 | 1 | Free |
| 2 | 2 | 500 |
| 3 | 4 | 1,500 |
| 4 | 6 | 3,000 |
| 5 | 8 | 5,000 |
| 6 | 16 | 7,500 |
| 7 | 24 | 10,000 |
| 8 | 32 | 15,000 |
| 9 | 48 | 20,000 |
| 10 | 64 | 30,000 |

When upgrading a chained pair the cost is `level_cost × number_of_pairs_in_chain`.

The upgrade level is stored on the chest item itself. Breaking a chest and placing it elsewhere preserves the upgrade — no progress is lost.

---

## Language

Two language files are included: `en` (English) and `it` (Italian). Set `language: en` or `language: it` in `config.yml`. Custom language files can be added to `plugins/ItemElevators/lang/`.
