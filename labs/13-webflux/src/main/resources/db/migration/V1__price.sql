create table price (
    id          bigserial primary key,
    symbol      varchar(10)    not null,
    amount      numeric(19, 4) not null check (amount > 0),
    recorded_at timestamptz    not null
);

create index price_symbol_recorded_at on price (symbol, recorded_at desc);
