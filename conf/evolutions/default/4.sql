# --- !Ups

alter table "order" add column "processed" BOOLEAN;
update "order" set "processed" = TRUE;
alter table "order" alter column "processed" SET NOT NULL;

# --- !Downs

alter table "order" drop column "processed";

