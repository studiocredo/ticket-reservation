# --- !Ups

alter table "show" add column "reservationStart" TIMESTAMP;
alter table "show" add column "reservationEnd" TIMESTAMP;


# --- !Downs

alter table "show" drop column "reservationStart";
alter table "show" drop column "reservationEnd";