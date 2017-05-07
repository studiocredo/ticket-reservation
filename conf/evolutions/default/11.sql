# --- !Ups

alter table "dvd" drop constraint "event_fk";
drop table "dvd";

create table "asset" ("id" SERIAL NOT NULL PRIMARY KEY,"event_id" BIGINT NOT NULL,"name" TEXT NOT NULL,"price" DECIMAL(21,2),"available-start" TIMESTAMP NOT NULL,"available-end" TIMESTAMP, "downloadable" BOOLEAN NOT NULL, "object-key" VARCHAR(256), "archived" BOOLEAN DEFAULT false NOT NULL);
alter table "asset" add constraint "event_fk" foreign key("event_id") references "event"("id") on update NO ACTION on delete NO ACTION;

# --- !Downs

alter table "asset" drop constraint "event_fk";
drop table "asset";

create table "dvd" ("id" SERIAL NOT NULL PRIMARY KEY,"event_id" BIGINT NOT NULL,"name" TEXT NOT NULL,"price" INTEGER NOT NULL,"available-start" TIMESTAMP NOT NULL,"available-end" TIMESTAMP,"archived" BOOLEAN DEFAULT false NOT NULL);
alter table "dvd" add constraint "event_fk" foreign key("event_id") references "event"("id") on update NO ACTION on delete NO ACTION;