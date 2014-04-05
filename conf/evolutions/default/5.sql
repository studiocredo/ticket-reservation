# --- !Ups

alter table "order" add column "comments" TEXT;

# --- !Downs

alter table "order" drop column "comments";

