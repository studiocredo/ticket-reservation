package be.studiocredo.reservations

import models.entities.{SeatWithStatus, SeatStatus, FloorPlan}
import models.entities.SeatStatus.SeatStatus

object ReservationEngine {
  def suggestSeats(quantity: Int, floorplan: FloorPlan, includes: List[SeatStatus] = List(SeatStatus.Free)): Either[String, List[SeatWithStatus]] = {
    val elegibleSeats = floorplan.seatsWithStatus.filter(seat => includes.contains(seat.status))
    elegibleSeats.length match {
      case l:Int if l > quantity => Right(elegibleSeats.take(quantity))
      case _ => Left("re.capacity.insufficient")
    }
  }
}

class ReservationEngine {

}
/*
  @@min_preference = @@fill_preference.values.map{|h| h.values}.flatten.inject(@@fill_preference.values.first.values.first){|min,x| min = x if x < min; min}
  @@max_preference = @@fill_preference.values.map{|h| h.values}.flatten.inject(@@fill_preference.values.first.values.first){|max,x| max = x if x > max; max}


  def self.total_seats
    @@row_size.inject(0){|sum,x| sum+x}
  end

  def suggest_seats(number, already_occupied = [])
    possible_seats = seat_data.inject([]) do |result,row_data|
      row = row_data[:row_number]
      if row_data[:seat_numbers].length-number < 0
        result
      else
        availability = row_data.values_at(*row_data[:seat_numbers]).map{|s| s == :free }
        possible_seats_on_row = (0..(row_data[:seat_numbers].length-number)).map do |index|
          if availability[index..(index+number-1)].all?{|seat_ok| seat_ok}
            (index..(index+number-1)).to_a
          else
            nil
          end
        end.compact
        result + possible_seats_on_row.map do |seat_indices|
          seats = row_data[:seat_numbers].values_at(*seat_indices)
          [ seats.map{|seat| "#{row}#{seat}"}, seats.inject(0){|sum, seat| sum += score(row,seat)}+heuristic(row,seat_indices, already_occupied)]
        end        
      end
    end
    possible_seats = possible_seats.sort_by{|possible_seat| possible_seat.last}
    raise "Could not find #{number} free seats on the same row." if possible_seats.empty?
    possible_seats.first.first
  end
  
  def suggest_seats_simple(number, already_occupied = [])
    result = []
    seat_data.each do |row_data|
      next if result.length >= number
      row_data[:seat_numbers].each do |seat_number|
        next if result.length >= number
        seat = "#{row_data[:row_number]}#{seat_number}"
        result <<  seat if row_data[seat_number] == :free
      end
    end
    result
  end
  
  private
  
  def score(row,seat)
    ( @@fill_preference[row] && @@fill_preference[row][seat] ) ? @@fill_preference[row][seat] : nil
  end
  
  def heuristic(row,seat_indices, already_occupied = [])
    result = 0
    # leaving one free seat to the left or right is punished with 5 points
    row_data = seat_data.find{|r| r[:row_number] == row}
    return 0 if row_data.nil?
    min_index = seat_indices.inject(seat_indices.first){|min, i| i < min ? i : min }
    max_index = seat_indices.inject(seat_indices.first){|max, i| i > max ? i : max }
    case min_index
      when 0 then result +=0
      when 1 then result +=5 if row_data[row_data[:seat_numbers][0]] == :free
      else result += 5 if row_data[row_data[:seat_numbers][min_index-1]] == :free && row_data[row_data[:seat_numbers][min_index-2]] != :free
    end
    case max_index
      when row_data[:seat_numbers].length-1 then result +=0
      when row_data[:seat_numbers].length-2 then result +=5 if row_data[row_data[:seat_numbers][row_data[:seat_numbers].length-1]] == :free
      else result += 5 if row_data[row_data[:seat_numbers][max_index+1]] == :free && row_data[row_data[:seat_numbers][max_index+2]] != :free
    end
    if !already_occupied.empty?
      #have seats next to already occupied seats is awarded
      #in other words, if already_occupied is not empty, all
      #combinations that are not next to an already occupied seed are
      #punished by adding max_prerence*length points, so
      #that any combination that has seats next to alread occupied
      #seats is preferred
      already_occupied_seats_in_row = already_occupied.map{|x| x =~ /([A-Za-z]+)(\d+)/; ($1 == row)? $2.to_i : nil}.compact 
      already_occupied_indices_in_row = already_occupied_seats_in_row.map{|x| row_data[:seat_numbers].index(x)}
      if already_occupied_indices_in_row.include?(min_index-1) || already_occupied_indices_in_row.include?(max_index+1)
        #they are neighbours
        result += 0
      else
        #punish!
        result += seat_indices.length * (@@max_preference+1)
      end
    end
    result
  end  


  def seat_numbers(size)
    if size % 2 == 0
      odd_last = size-1
      even_last = size
    else
      odd_last = size
      even_last = size-1
    end
    odd_numbers = []
    (1..odd_last).step(2){|x| odd_numbers << x }
    even_numbers = []
    (2..even_last).step(2){|x| even_numbers << x }
    odd_numbers + even_numbers.reverse
  end
  
*/

