create table orders (
    id         uuid primary key,
    sku        varchar(64) not null,
    quantity   integer     not null check (quantity > 0),
    created_at timestamptz not null,
    version    bigint      not null
);

-- Transactional outbox: written in the same transaction as the order, published later by the relay.
create table outbox_event (
    id             uuid primary key,
    aggregate_type varchar(64)  not null,
    aggregate_id   uuid         not null,
    event_type     varchar(128) not null,
    topic          varchar(255) not null,
    payload        text         not null,
    created_at     timestamptz  not null,
    sent_at        timestamptz
);
-- Partial index: the relay only ever scans unsent rows.
create index outbox_event_unsent_idx on outbox_event (created_at) where sent_at is null;

create table stock (
    sku       varchar(64) primary key,
    available integer     not null check (available >= 0)
);

-- Idempotent consumer: one row per handled event id. If several consumer groups share this
-- database, make the key (consumer_group, event_id).
create table processed_message (
    event_id     uuid primary key,
    processed_at timestamptz not null
);

insert into stock (sku, available) values ('BOOK-1', 100), ('PEN-1', 500);
