-- M2: users, refresh tokens, orders. Same conventions as V1: INCREMENT BY 50 matches allocationSize = 50,
-- and every foreign key gets its own index (Postgres does not create one for you).

create sequence users_seq increment by 50;
create table users (
    id            bigint primary key,
    username      varchar(50)  not null unique,
    password_hash varchar(100) not null, -- DelegatingPasswordEncoder output, e.g. {bcrypt}$2a$10$...
    role          varchar(20)  not null check (role in ('ADMIN', 'CUSTOMER'))
);

-- Opaque refresh tokens. Only the SHA-256 of the token is stored, so a leaked table yields no usable token.
-- A login starts a family. Rotation marks the presented token used and issues the next one in the same family.
-- Presenting a used token again marks the whole family used (revoked).
create table refresh_tokens (
    token_hash char(64)    primary key,
    family_id  uuid        not null,
    user_id    bigint      not null references users (id) on delete cascade,
    expires_at timestamptz not null,
    used       boolean     not null default false
);
create index refresh_tokens_family_idx on refresh_tokens (family_id);
create index refresh_tokens_user_idx on refresh_tokens (user_id);

create sequence orders_seq increment by 50;
create table orders (
    id          bigint primary key,
    customer_id bigint         not null references users (id),
    total       numeric(14, 2) not null check (total >= 0),
    created_at  timestamptz    not null
);
-- Serves "my orders, newest first": the customer filter and the default sort in one index.
create index orders_customer_created_idx on orders (customer_id, created_at desc);

create sequence order_lines_seq increment by 50;
create table order_lines (
    id         bigint primary key,
    order_id   bigint         not null references orders (id) on delete cascade,
    product_id bigint         not null references products (id), -- RESTRICT: a sold product cannot be hard-deleted (409)
    quantity   integer        not null check (quantity > 0),
    unit_price numeric(12, 2) not null check (unit_price > 0)    -- price snapshot at purchase time
);
create index order_lines_order_idx on order_lines (order_id);
create index order_lines_product_idx on order_lines (product_id);
