-- One row per player per match (a match has ten). The primary key makes inserts idempotent.
CREATE TABLE match_performance (
    match_id         TEXT             NOT NULL,
    puuid            TEXT             NOT NULL,
    queue_id         INTEGER          NOT NULL,
    game_creation    BIGINT           NOT NULL,
    duration_seconds BIGINT           NOT NULL,
    team_position    TEXT,
    win              BOOLEAN          NOT NULL,
    kills            INTEGER          NOT NULL,
    deaths           INTEGER          NOT NULL,
    assists          INTEGER          NOT NULL,
    agr              DOUBLE PRECISION NOT NULL,
    frm              DOUBLE PRECISION NOT NULL,
    vis              DOUBLE PRECISION NOT NULL,
    tea              DOUBLE PRECISION NOT NULL,
    sur              DOUBLE PRECISION NOT NULL,
    imp              DOUBLE PRECISION NOT NULL,
    match_score      DOUBLE PRECISION NOT NULL,
    match_rating     DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (match_id, puuid)
);

CREATE INDEX match_performance_puuid_recent_idx ON match_performance (puuid, game_creation DESC);

-- Players looked up on the site: only they get a card.
CREATE TABLE requested_player (
    puuid        TEXT        PRIMARY KEY,
    game_name    TEXT        NOT NULL,
    tag_line     TEXT        NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL
);
