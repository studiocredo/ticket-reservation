# --- !Ups

alter table "payment" alter column "order_id" DROP NOT NULL;
alter table "payment" add column "payment_type" TEXT NOT NULL;
alter table "payment" add column "import_id" TEXT;
create unique index "idx_import_id" on "payment" ("import_id");
alter table "payment" add column "message" TEXT;
alter table "payment" add column "archived" BOOLEAN DEFAULT false NOT NULL;


# --- !Downs

alter table "payment" drop column "archived";
alter table "payment" drop column "message";
drop index "index idx_import_id";
alter table "payment" drop column "import_id";
alter table "payment" drop column "payment_type";
alter table "payment" alter column "order_id" SET NOT NULL;

