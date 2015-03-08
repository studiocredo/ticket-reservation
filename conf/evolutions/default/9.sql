# --- !Ups

alter table "event" add column "template" TEXT;


# --- !Downs

alter table "event" drop column "template";