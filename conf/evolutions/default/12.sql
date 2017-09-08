# --- !Ups

alter table "event" add column "quota" TEXT;


# --- !Downs

alter table "event" drop column "quota";