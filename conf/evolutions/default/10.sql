# --- !Ups

alter table "venue" add column "admin_label" TEXT;


# --- !Downs

alter table "venue" drop column "admin_label";