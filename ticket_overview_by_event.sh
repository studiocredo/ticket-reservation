event=$1

cat <<EOF
Copy(select o."billing-name", CASE WHEN o."billing-name" = u.name THEN '' ELSE u.name END, s.date, st.seat, substring(o.comments from 0 for 32) from "order" o join "user" u on o.user_id = u.id join "order-ticket" t on o.id = t.order_id join "order-ticket-seat" st on st.ticket_order_id = t.id join "show" s on t.show_id = s.id join "event" e on s.event_id = e.id where e.name = '$event' order by s.date, substring(st.seat from 1 for 1), 1 - to_number(substring(st.seat from 2),'99') % 2, to_number(substring(st.seat from 2),'99')) TO STDOUT WITH CSV;
EOF
