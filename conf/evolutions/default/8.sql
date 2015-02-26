# --- !Ups

alter table "order" add column "archived" BOOLEAN DEFAULT false NOT NULL;


# --- !Downs

alter table "order" drop column "archived";