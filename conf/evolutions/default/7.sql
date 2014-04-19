# --- !Ups

create table "ticket_distribution_log" ("order_id" BIGINT NOT NULL, "serial" INTEGER NOT NULL, "date" TIMESTAMP NOT NULL);
alter table "ticket_distribution_log" add constraint "order_fk" foreign key("order_id") references "order"("id") on update NO ACTION on delete NO ACTION;
create unique index "idx_order_id_serial" on "ticket_distribution_log" ("order_id", "serial");

# --- !Downs

alter table "ticket_distribution_log" drop constraint "order_fk";
drop table "ticket_distribution_log";

