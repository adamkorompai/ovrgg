# SCORING.md — Modèle de notation OVR.gg (League of Legends, V1)

> Spec de référence pour `stats-engine` et `card-service`. Toute modification de formule passe par ce fichier.
> Les noms de champs Riot sont indicatifs : vérifier dans la doc MATCH-V5 / TIMELINE-V5 avant d'implémenter.

## 1. Principe

La note ne repose jamais sur 2 ou 3 stats. Le modèle :

1. extrait ~35 métriques par match (fin de partie + timeline),
2. les normalise (par minute, par part d'équipe, vs adversaire direct),
3. les compare à une **baseline du même rang et du même rôle** → percentile 0–100,
4. les regroupe en **sous-stats → 6 attributs**,
5. pondère les attributs **selon le rôle** → score de performance du match,
6. agrège les matchs (récence + fiabilité statistique),
7. place le résultat dans la **plage du rang officiel** → note OVR.

## 2. Sources de données

- **MATCH-V5** (`/lol/match/v5/matches/{matchId}`) : stats de fin de partie par participant + objet `challenges`.
- **TIMELINE-V5** (`/lol/match/v5/matches/{matchId}/timeline`) : frames par minute (or, XP, CS, position) + événements (kills avec position, wards, objectifs, achats).
- **LEAGUE-V4** : rang officiel du joueur (définit la plage de note).

## 3. Filtres

- Files classées uniquement (Solo/Duo, Flex) pour la note. Les autres modes peuvent alimenter des badges, jamais la note.
- Exclure les remakes (< 5 min) et les parties où le joueur est clairement AFK (activité quasi nulle).
- Minimum **10 parties classées** pour afficher une carte "officielle", sinon carte marquée **provisoire**.

## 4. Attributs et sous-stats

Chaque attribut = moyenne pondérée de ses sous-stats (percentiles 0–100). Les pondérations internes sont des valeurs de départ à calibrer.

### AGR — Agressivité
| Sous-stat | Calcul | Poids |
|---|---|---|
| Dégâts aux champions / min | `totalDamageDealtToChampions / durée` | 30 |
| Part des dégâts de l'équipe | `teamDamagePercentage` | 25 |
| Solo kills | `soloKills` | 20 |
| Kills + assists avant 15 min | timeline (événements CHAMPION_KILL) | 15 |
| Pression sous tour ennemie | `killsNearEnemyTurret` | 10 |

### FRM — Économie
| Sous-stat | Calcul | Poids |
|---|---|---|
| CS / min | `(totalMinionsKilled + neutralMinionsKilled) / durée` | 30 |
| CS à 10 min | `laneMinionsFirst10Minutes` | 20 |
| Écart d'or à 15 min vs adversaire de lane | timeline, frame 15 | 25 |
| Avance max en CS sur l'adversaire | `maxCsAdvantageOnLaneOpponent` | 15 |
| Or / min | `goldPerMinute` | 10 |

### VIS — Vision
| Sous-stat | Calcul | Poids |
|---|---|---|
| Score de vision / min | `visionScorePerMinute` | 35 |
| Avantage de vision vs adversaire | `visionScoreAdvantageLaneOpponent` | 20 |
| Wards détruites / min | `wardsKilled / durée` | 20 |
| Control wards achetées | `visionWardsBoughtInGame` | 15 |
| Wards posées / min | `wardsPlaced / durée` | 10 |

### TEA — Teamplay
| Sous-stat | Calcul | Poids |
|---|---|---|
| Participation aux kills | `killParticipation` | 35 |
| Participation aux objectifs épiques | takedowns dragon/baron/héraut/grubs (timeline) | 25 |
| Soins et boucliers sur alliés | `effectiveHealAndShielding` | 15 |
| Contrôle de foule infligé | `timeCCingOthers` | 15 |
| Assists / min | `assists / durée` | 10 |

### SUR — Survie
| Sous-stat | Calcul | Poids |
|---|---|---|
| Morts / min (inversé) | `deaths / durée` | 35 |
| Morts avant 15 min (inversé) | timeline | 20 |
| % du temps passé mort (inversé) | `totalTimeSpentDead / durée` | 20 |
| Dégâts encaissés par mort | `totalDamageTaken / max(deaths,1)` | 15 |
| Survies à très faible PV | `survivedSingleDigitHpCount` | 10 |

### IMP — Impact
| Sous-stat | Calcul | Poids |
|---|---|---|
| Dégâts aux bâtiments | `damageDealtToBuildings` | 25 |
| Efficacité (dégâts / or) | `totalDamageDealtToChampions / goldEarned` | 20 |
| Plaques de tour | `turretPlatesTaken` | 15 |
| Implication first blood / première tour | `firstBloodKill/Assist`, `firstTowerKill/Assist` | 15 |
| Dégâts aux objectifs | `damageDealtToObjectives` | 15 |
| Vols d'objectifs épiques | `epicMonsterSteals` | 10 |

## 5. Normalisation et baselines

- Chaque sous-stat est convertie en **percentile** par rapport à la distribution du **même palier de rang × même rôle** (ex. Or / Support).
- Les métriques "inversées" (morts…) : `percentile = 100 - percentile_brut`.
- Baselines recalculées chaque nuit à partir des matchs ingérés (job batch). Cold start : dataset de seed ingéré au préalable.
- Les métriques sont winsorisées (p1–p99) avant calcul pour limiter l'effet des parties aberrantes.
- Attention à la corrélation : une même info ne doit pas compter dans deux attributs avec un poids fort.

## 6. Pondération par rôle → score de match

| Rôle | AGR | FRM | VIS | TEA | SUR | IMP |
|---|---|---|---|---|---|---|
| Top | 20 | 20 | 10 | 10 | 15 | 25 |
| Jungle | 15 | 10 | 15 | 25 | 10 | 25 |
| Mid | 20 | 20 | 10 | 15 | 15 | 20 |
| ADC | 15 | 25 | 5 | 15 | 20 | 20 |
| Support | 10 | 5 | 30 | 30 | 15 | 10 |

`matchScore = Σ(attribut × poids_rôle) / 100` → valeur 0–100.

### Ajustements de contexte (petits, plafonnés à ±5 au total)
- **Niveau du lobby** : rang moyen des 9 autres joueurs vs rang du joueur.
- **Durée** : parties < 20 min (ff) ont un poids réduit dans l'agrégation (moins d'information).
- **Victoire/défaite** : ±2 maximum. La note juge la performance, pas le résultat.

### Note de match affichée (échelle /10)

- Chaque partie a une note affichée sur 10, distincte de l'OVR pour éviter la confusion.
- `noteMatch = 3.0 + 7.0 × matchScore / 100`, arrondie à 0.1 (une partie moyenne ≈ 6.5).
- Couleurs : < 6.0 rouge, 6.0–6.9 orange, 7.0–8.4 vert, ≥ 8.5 bleu.
- Les **10 participants** de chaque partie sont notés (données déjà disponibles dans MATCH-V5).
- Badges : **MVP** = meilleure note de la partie (équipe gagnante), **ACE** = meilleure note de l'équipe perdante.
- **Forme** de la carte : moyenne des 5 dernières notes vs note globale → flèche ↑ (> +0.5), ↓ (< -0.5), → sinon.

## 7. Agrégation sur plusieurs matchs

- Fenêtre : 30 dernières parties classées.
- **Récence** : décroissance exponentielle, demi-vie de 10 parties.
- **Fiabilité (shrinkage bayésien)** : `scoreFinal = (n × moyenne + k × 50) / (n + k)` avec `k = 8`. Peu de parties → note tirée vers la moyenne.
- **Régularité** : écart-type des `matchScore` → badge "Régulier" / "Imprévisible", n'affecte pas la note.

## 8. Mapping dans la plage du rang → OVR

| Rang officiel | Plage OVR |
|---|---|
| Fer – Bronze | 40–55 |
| Argent | 52–64 |
| Or | 60–72 |
| Platine – Émeraude | 68–80 |
| Diamant | 75–85 |
| Master – Grandmaster | 85–92 |
| Top du ladder (Challenger, top 500 région) | 93–99 |

`OVR = min + (max - min) × scoreFinal / 100`, arrondi à l'entier.
Les plages se chevauchent volontairement : un très bon Argent peut dépasser un mauvais Or.

Les **attributs affichés** sur la carte sont les percentiles agrégés (pas bornés par la plage du rang) : un Or peut afficher 90 en VIS.

## 9. Transparence et règles Riot

- La note n'est **jamais** utilisée pour trier ou classer des joueurs entre eux.
- La page "Comment est calculée ma note" explique ce modèle publiquement.
- Chaque carte affiche les 3 sous-stats qui ont le plus tiré la note vers le haut et vers le bas (explicabilité).

## 10. Tests attendus

- Tests unitaires par sous-stat avec des matchs JSON réels anonymisés (fixtures).
- Tests de propriété : une stat améliorée ne fait jamais baisser l'attribut concerné.
- Test de régression : un jeu de 20 joueurs de référence dont la note ne doit pas bouger sans changement volontaire du modèle.
