# --- !Ups

create table "event_pricing" ("id" BIGINT NOT NULL,"category" TEXT NOT NULL, "price" DECIMAL(21,2) NOT NULL);
create unique index "idx_event_category" on "event_pricing" ("id","category");
alter table "event_pricing" add constraint "event_fk" foreign key("id") references "event"("id") on update NO ACTION on delete NO ACTION;

# --- !Downs

alter table "event_pricing" drop constraint "event_fk";
drop table "event_pricing";
