event=$1

echo "update event set archived = true where name = '$1';"
echo "update show as s set archived = true from event e where s.event_id = e.id and e.name = '$1';"
echo "with processed_archived_orders as (select o.id from \"order\" o left join \"order-ticket\" ot on o.id = ot.order_id left join \"show\" s on ot.show_id = s.id where o.processed = true group by o.id having every(s.archived = true)), processed_payed_orders as (select p.order_id from \"payment\" p left join \"order\" o on p.order_id = o.id left join \"order-ticket\" ot on o.id = ot.order_id left join \"order-ticket-seat\" ost on ot.id = ost.ticket_order_id where o.processed = true group by p.id having sum(ost.price) = p.amount), free_orders as (select o.id from \"order\" o left join \"order-ticket\" ot on o.id = ot.order_id left join \"order-ticket-seat\" ost on ot.id = ost.ticket_order_id where o.processed = true group by o.id having sum(ost.price) = 0) update \"order\" set archived = true where id in (select id from processed_archived_orders) and (id in (select order_id from processed_payed_orders) or id in (select id from free_orders));"
echo "with archived_payments as (select p.id from \"payment\" p left join \"order\" o on p.order_id = o.id group by p.id having every(o.processed = true) and every(o.archived = true)) update \"payment\" set archived = true where id in (select id from archived_payments);"