event=$1

enddate=$2
startdate=$3

if [ -z "$enddate" ]; then
	echo "Copy (select p.date::date, p.debtor, p.amount, round(p.amount*0.79,2) as nobtw, e.name, concat(count(seat.seat), ' ticket(s)') as tickets from \"payment\" p left join \"order\" o on p.order_id = o.id left join \"order-ticket\" t on o.id = t.order_id left join \"show\" s on t.show_id = s.id left join \"event\" e on s.event_id = e.id left join \"order-ticket-seat\" seat on t.id = seat.ticket_order_id group by p.date, p.debtor, p.amount, e.name having e.name = '$event' order by p.date) To STDOUT With CSV;"
elif [ -z "$startdate" ]; then
	echo "Copy (select p.date::date, p.debtor, p.amount, round(p.amount*0.79,2) as nobtw, e.name, concat(count(seat.seat), ' ticket(s)') as tickets from \"payment\" p left join \"order\" o on p.order_id = o.id left join \"order-ticket\" t on o.id = t.order_id left join \"show\" s on t.show_id = s.id left join \"event\" e on s.event_id = e.id left join \"order-ticket-seat\" seat on t.id = seat.ticket_order_id group by p.date, p.debtor, p.amount, e.name having e.name = '$event' and p.date <= '$enddate'::date order by p.date) To STDOUT With CSV;"
else
    startdate=$2
    enddate=$3
	echo "Copy (select p.date::date, p.debtor, p.amount, round(p.amount*0.79,2) as nobtw, e.name, concat(count(seat.seat), ' ticket(s)') as tickets from \"payment\" p left join \"order\" o on p.order_id = o.id left join \"order-ticket\" t on o.id = t.order_id left join \"show\" s on t.show_id = s.id left join \"event\" e on s.event_id = e.id left join \"order-ticket-seat\" seat on t.id = seat.ticket_order_id group by p.date, p.debtor, p.amount, e.name having e.name = '$event' and p.date BETWEEN '$startdate'::date AND '$enddate'::date order by p.date) To STDOUT With CSV;"
fi