# --- !Ups

create table "payment" ("id" SERIAL NOT NULL PRIMARY KEY,"order_id" BIGINT NOT NULL,"debtor" TEXT NOT NULL,"amount" DECIMAL(21,2) NOT NULL,"details" TEXT,"date" TIMESTAMP NOT NULL);
alter table "payment" add constraint "order_fk" foreign key("order_id") references "order"("id") on update NO ACTION on delete NO ACTION;

# --- !Downs

alter table "payment" drop constraint "order_fk";
drop table "payment";

