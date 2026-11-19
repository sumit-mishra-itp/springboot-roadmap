-- Flyway owns the schema. Hibernate only VALIDATES it (spring.jpa.hibernate.ddl-auto=validate).
-- Each sequence uses INCREMENT BY 50 to match @SequenceGenerator(allocationSize = 50). The pooled optimizer
-- hands out 50 ids per nextval() call, and a mismatch fails startup validation.

create sequence categories_seq increment by 50;
create table categories (
    id   bigint primary key,
    name varchar(100) not null unique
);

create sequence products_seq increment by 50;
create table products (
    id          bigint primary key,
    sku         varchar(40)    not null unique,
    name        varchar(200)   not null,
    description varchar(2000),
    price       numeric(12, 2) not null check (price > 0),
    stock       integer        not null check (stock >= 0), -- last line of defence against overselling
    version     bigint         not null,                    -- @Version optimistic lock counter (orders decrement stock)
    category_id bigint         not null references categories (id) -- RESTRICT: a used category cannot be deleted
);
create index products_category_idx on products (category_id); -- Postgres does NOT index foreign keys for you
