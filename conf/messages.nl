# Default messages

# --- Constraints
constraint.required=Verplicht
constraint.min=Minimum waarde: {0}
constraint.max=Maximum waarde: {0}
constraint.minLength=Minimum lengte: {0}
constraint.maxLength=Maximum lengte: {0}
constraint.email=Email

# --- Formats
format.date=Datum (''{0}'')
format.numeric=Getal
format.numeric.positive=Getal groter dan 0
format.money.positive=Bedrag groter dan 0
format.real=Kommagetal
format.money=Bedrag in euro
format.paymentType=Betalingswijze
format.id=ID
format.event.userquota=Komma lijst van aantal gebruikers en quota, bv: '1:4, 2:6'

# --- Errors
error.invalid=Ongeldige waarde
error.invalid.java.util.Date=Ongeldige datum
error.required=Dit veld is verplicht
error.number=Waarde moet een getal zijn
error.real=Waarde moet een kommagetal zijn
error.real.precision=Waarde moet een kommagetal zijn met maximaal {0} cijfer(s) waarvan {1} na de komma
error.min=Waarde moet groter zijn dan of gelijk zijn aan {0}
error.min.strict=Waarde moet strikt groter zijn dan {0}
error.max=Waarde moet kleiner zijn dan of gelijk zijn aan {0}
error.max.strict=Waarde moet strik kleiner zijn dan {0}
error.minLength=Minimale lengte is {0}
error.maxLength=Maximale lengte is {0}
error.email=Waarde moet een geldig e-mail adres zijn
error.pattern=Waarde moet voldoen aan {0}
error.money.invalid=Ongeldig bedrag
error.paymentType.invalid=Ongeldige betalingswijze
error.date=Ongeldige datum
error.id=Ongeldige ID waarde
error.event.userquota.keys.consecutive=Sleutels moeten opeenvolgende waarden zijn beginnend van 1
error.event.userquota.values.positive=Waarden moeten positieve getallen zijn
error.event.userquota.values.increasing=Waarden moeten oplopend zijn
error.event.quota.default_gt_values=Standaard quota moet minstens even groot zijn als gebruikersquota
error.event.price.category.unique=Categorie moet uniek zijn
error.event.not_found=Evenement niet gevonden
error.show.date.start=Start reservaties moet binnen periode van het evenement liggen
error.show.date.end=Einde reservaties moet binnen periode van het evenement liggen
error.show.date.before=Start reservaties moet vóór einde reservaties zijn

#No need to translate JSON conversion errors
#error.expected.date=Datum verwacht
#error.expected.date.isoformat=ISO datum verwacht
#error.expected.jodadate.format=Joda datum waarde verwacht
#error.expected.jsarray=Array value expected
#error.expected.jsboolean=Boolean value expected
#error.expected.jsnumber=Number value expected
#error.expected.jsobject=Object value expected
#error.expected.jsstring=String value expected
#error.expected.jsnumberorjsstring=String or number expected
#error.expected.keypathnode=Node value expected

#error.path.empty=Empty path
#error.path.missing=Missing path
#error.path.result.multiple=Multiple results for the given path

# --- Prereservations
prereservations.success=Pre-reservaties bewaard
prereservations.quota.exceeded=Maximaal aantal pre-reservaties overschreden
#prereservations.quota.success
prereservations.capacity.exceeded=Er zijn onvoldoende vrije plaatsen voor het evenement {0} op {1}, gelieve {2} pre-reservatie(s) te verwijderen.
#prereservations.capacity.success

# --- Reservations
reservations.seat.unknown=Plaats {0} bestaat niet voor locatie {1}
reservations.success=Reservaties bewaard

# --- Events
#event.save.success
event.save.res.before=Start reservaties moet vóór einde reservaties zijn
event.save.preres.before=Start pre-reservaties moet vóór einde pre-reservaties zijn
event.save.res.both=Start en einde reservaties moeten beide wel óf beide niet ingevuld zijn
event.save.preres.both=Start en einde pre-reservaties moeten beide wel óf beide niet ingevuld zijn
#eventprice.save.success
eventprice.save.positive=Bedrag moet positief zijn

# --- Users
#user.update.success
user.update.notfound=Gebruiker niet gevonden
#user.update.name.accepted
user.update.name.used=Gebruikersnaam {0} is reeds in gebruik
#user.update.name.unchanged

# --- Floorplan
floorplan.invalid.type=Zaalplan bevat ongeldig stoeltype
floorplan.invalid.notunique=Zaalplan bevat niet-unieke plaatsen: {0}
floorplan.invalid.preference.notpositive=Waarde voor voorkeur van opvullen moet strikt positief zijn
#floorplan.save.success

# --- Pricing
pricing.standard=Standaard Ticket

# --- Payment
#payment.insert.success
#payment.update.success
#payment.update.notfound

# --- Payment type options
options.paymenttype.wire=Overschrijving
options.paymenttype.cash=Cash
options.paymenttype.online=On-line
