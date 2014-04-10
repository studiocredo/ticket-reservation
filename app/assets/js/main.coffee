"use strict"
App = angular.module("credo", ["floorplan", "counterInput", "dismissModal"])

Floorplan = angular.module("floorplan", ["ngDragDrop", "ui.sortable", "ng", "ui.bootstrap"])

Floorplan.controller "FloorPlanEditCtrl", ($scope, $http) ->
    # -------
    removeContent = (row, index) ->
        row.content.splice index, 1
    SEAT_NORMAL = "normal"
    SEAT_VIP = "vip"
    SEAT_DISABLED = "disabled"
    PAINT_DELETE = "rm"
    $scope.messages = {}
    $scope.rows = []
    $scope.init = (venueId) ->
        $scope.venueId = venueId
        $http.get(jsRoutes.controllers.admin.Floorplans.ajaxFloorPlan($scope.venueId).url).success (plan) ->
            $scope.plan = plan
            $scope.rows = plan.rows


    $scope.save = ->
        $scope.messages = []
        call = $http.post(jsRoutes.controllers.admin.Floorplans.ajaxSaveFloorPlan($scope.venueId).url, $scope.plan)
        call.success (data) ->
            $scope.messages.push
                type: "success"
                msg: "Bewaard"


        call.error (data) ->
            $scope.messages.push
                type: "danger"
                msg: "Bewaren mislukt"


    $scope.paint = SEAT_NORMAL
    $scope.paintNormal = ->
        $scope.paint = SEAT_NORMAL

    $scope.paintVip = ->
        $scope.paint = SEAT_VIP

    $scope.paintDisabled = ->
        $scope.paint = SEAT_DISABLED

    $scope.paintDelete = ->
        $scope.paint = PAINT_DELETE

    $scope.paintSpacer = (row, index) ->
        removeContent row, index  if $scope.paint is PAINT_DELETE

    $scope.paintSeat = (row, index) ->
        if $scope.paint is PAINT_DELETE
            removeContent row, index
        else
            row.content[index].kind = $scope.paint


    # -------
    $scope.addRow = ->
        $scope.rows.push
            content: [
                ct: "seat"
                kind: SEAT_NORMAL
            ]
            vspace: 0

    $scope.removeRow = (index) ->
        $scope.rows.splice index, 1

    $scope.moveRowUp = (index) ->
        newPos = Math.max 0, index - 1
        value = $scope.rows[index]
        $scope.rows.splice index, 1
        $scope.rows.splice newPos, 0, value

    $scope.moveRowDown = (index) ->
        newPos = Math.min $scope.rows.length - 1,index + 1
        value = $scope.rows[index]
        $scope.rows.splice index, 1
        $scope.rows.splice newPos, 0, value

    $scope.copyRow = (index) ->
        $scope.rows.splice index, 0, angular.copy $scope.rows[index]

    $scope.addSeat = (row) ->
        row.content.push
            ct: "seat"
            kind: $scope.paint


    $scope.spacerSize = 2
    $scope.addSpacer = (row, size) ->
        row.content.push
            ct: "spacer"
            width: size

    $scope.verticalSpacerSize = 0
    $scope.addVerticalSpacer = (row, size) ->
        row.vspace = size

    $scope.editRow = (row) ->
        $scope.selectedRow = row

    $scope.rowSortableOptions =
        axis: "x"
        cursor: "move"
        revert: true

Floorplan.controller "FloorPlanCtrl", ($scope, $http) ->
    $http.get(jsRoutes.controllers.Events.ajaxFloorplan($scope.venue).url).success (plan) ->
        $scope.plan = plan
        $scope.rows = plan.rows

Floorplan.directive 'floorplan', () ->
    restrict: 'EA'
    template: """
<div class="fp fp-fancy">
    <div class="row seat-row vspacer-{{row.vspace}}" data-ng-repeat="row in rows">
        <div data-ng-model="row.content">
            <div class="content" data-ng-repeat="content in row.content">
                <div class="spacer spacer-{{content.width}} no-select" data-ng-if="content.ct == 'spacer'"></div>
                <div class="clickable seat seat-{{content.kind}} no-select" data-ng-if="content.ct == 'seat'">{{content.id.name}}</div>
            </div>
        </div>
    </div>
    <p class="text-center">Podium</p>
</div>
"""
    scope:
        venue: '='
    controller: 'FloorPlanCtrl'

Floorplan.controller "ShowAvailabilityCtrl", ($scope, $http) ->
     $http.get(jsRoutes.controllers.Events.ajaxAvailabilityFloorplan($scope.show).url).success (plan) ->
             $scope.plan = plan
             $scope.rows = plan.rows

Floorplan.directive 'availabilityFloorplan', () ->
    restrict: 'EA'
    template: """
<div class="fp fp-fancy">
    <div class="row seat-row vspacer-{{row.vspace}}" data-ng-repeat="row in rows">
        <div data-ng-model="row.content" class="span-12">
            <div class="content" data-ng-repeat="content in row.content">
                <div class="spacer spacer-{{content.width}} no-select" data-ng-if="content.ct == 'spacer'"></div>
                <div class="seat seat-{{content.kind}} no-select" data-ng-if="content.ct == 'seat'">{{content.id.name}}</div>
                <div class="seat seat-status-{{content.status}} no-select" data-ng-if="content.ct == 'seat-status'">{{content.id.name}}</div>
            </div>
        </div>
    </div>
    <p class="text-center">Podium</p>
</div>
"""
    scope:
        show: '='
    controller: 'ShowAvailabilityCtrl'

Floorplan.controller "OrderAdminFloorpanCtrl", ($scope, $http) ->
     $http.get(jsRoutes.controllers.admin.Orders.ajaxFloorplan($scope.show).url).success (plan) ->
             $scope.plan = plan
             $scope.rows = plan.rows

Floorplan.directive 'orderAdminFloorplan', () ->
    restrict: 'EA'
    template: """
<div class="fp fp-fancy">
    <div class="row seat-row vspacer-{{row.vspace}}" data-ng-repeat="row in rows">
        <div data-ng-model="row.content" class="span-12">
            <div class="content" data-ng-repeat="content in row.content">
                <div class="spacer spacer-{{content.width}} no-select" data-ng-if="content.ct == 'spacer'"></div>
                <div class="seat seat-{{content.kind}} no-select" data-ng-if="content.ct == 'seat'">{{content.id.name}}</div>
                <div class="seat seat-status-{{content.status}} no-select" data-ng-if="content.ct == 'seat-status'" data-tooltip-placement="top" data-tooltip="{{content.comment}}" data-tooltip-trigger="mouseenter">{{content.id.name}}</div>
            </div>
        </div>
    </div>
    <p class="text-center">Podium</p>
</div>
"""
    scope:
        show: '='
    controller: 'OrderAdminFloorpanCtrl'


MOVE_BEST = "MOVE_BEST"
UPDATE_SEAT_SELECTION = "UPDATE_SEAT_SELECTION"
UPDATE_TIMEOUT = "SET_TIMEOUT"
CLEAR_SEAT_SELECTION = "CLEAR_SEAT_SELECTION"
SELECT_ALL = "SELECT_ALL"
Floorplan.controller "ReservationFloorplanCtrl", ($scope, $http, $timeout) ->
  $scope.moveBest = () ->
    $scope.$broadcast(MOVE_BEST)
  $scope.clearSelection = () ->
    $scope.$broadcast(CLEAR_SEAT_SELECTION)
  $scope.selectAll = () ->
    $scope.$broadcast(SELECT_ALL)

  $scope.timeout = 0
  $scope.millis = 0
  $scope.timeoutLatch = false
  $scope.$on(UPDATE_TIMEOUT, (event, timeout) ->
    $scope.timeout = timeout
  )

  updateTime = () ->
    $scope.millis = new Date($scope.timeout) - new Date();
    if ($scope.millis > 0)
        $scope.timeoutLatch = true
    $scope.ticker = $timeout(updateTime, 1000)

  updateTime()

  $scope.humanTimeout = () ->
    return Math.floor((($scope.millis / (60000)) % 60)) + " min " + Math.floor(($scope.millis / 1000) % 60) + " sec"

  $scope.$on("$destroy", () ->
    $timeout.cancel($scope.ticker)
  )

  $scope.selected = []
  $scope.$on(UPDATE_SEAT_SELECTION, (event, selection) ->
    $scope.selected = selection
  )
  $scope.workaround = 5




Floorplan.directive 'reservationFloorplan', () ->
    restrict: 'EA'
    template: """
<div class="fp fp-fancy">
    <div class="row seat-row vspacer-{{row.vspace}}" data-ng-repeat="row in rows">
        <div data-ng-model="row.content" class="span-12">
            <div class="content" data-ng-repeat="content in row.content">
                <div class="spacer spacer-{{content.width}} no-select" data-ng-if="content.ct == 'spacer'"></div>
                <div class="seat seat-{{content.kind}} no-select" data-ng-if="content.ct == 'seat'">{{content.id.name}}</div>
                <div class="seat seat-status-{{content.status}} no-select" data-ng-if="content.ct == 'seat-status' && content.status == 'free'" data-ng-click="claim(content.id.name)">{{content.id.name}}</div>
                <div class="seat seat-status-{{seatStatus(content.id.name)}} no-select" data-ng-if="content.ct == 'seat-status' && content.status == 'mine'" data-ng-click="toggleSelect(content.id.name)">{{content.id.name}}</div>
                <div class="seat seat-status-{{content.status}} no-select" data-ng-if="content.ct == 'seat-status' && content.status != 'free' && content.status != 'mine'">{{content.id.name}}</div>
            </div>
        </div>
    </div>
    <p class="text-center">Podium</p>
</div>
"""
    scope:
        showId: '@'
        orderId: '@'
    controller: ($scope, $http, $timeout, $interval) ->
      $scope.planSeq = 0
      errorHandler = (response) ->
        if (response && response.redirect)
          document.location.href = response.redirect

      update = (response) ->
        if ($scope.planSeq < response.seq)
          $scope.planSeq = response.seq
          $scope.plan = response.plan
          $scope.rows = response.plan.rows
          $scope.$emit(UPDATE_TIMEOUT, response.timeout)

       updateAndClearSelected = (response) ->
         update(response)
         $scope.clearSelected()

      $scope.claim = (seat) ->
        payload = {target: {name:seat}}
        if ($scope.selected.length > 0)
            payload.seats = []
            angular.forEach($scope.selected, (seat) ->
                payload.seats.push({name: seat})
            )
            $http.post(jsRoutes.controllers.Orders.ajaxMove($scope.showId, $scope.orderId).url, payload).success(updateAndClearSelected).error(errorHandler)

      $scope.$on(MOVE_BEST, () ->
        $http.post(jsRoutes.controllers.Orders.ajaxMoveBest($scope.showId, $scope.orderId).url).success(updateAndClearSelected).error(errorHandler)
      )

      $scope.selected = []
      $scope.toggleSelect = (seat) ->
        idx = $scope.selected.indexOf(seat)
        if (idx == -1)
          $scope.selected.push(seat)
          $scope.selected.sort()
        else
          $scope.selected.splice(idx, 1)
        $scope.$emit(UPDATE_SEAT_SELECTION, $scope.selected)
      $scope.$on(CLEAR_SEAT_SELECTION, () -> $scope.clearSelected())
      $scope.clearSelected = () ->
        $scope.selected = []
        $scope.$emit(UPDATE_SEAT_SELECTION, $scope.selected)
      $scope.$on(SELECT_ALL, () -> $scope.selectAll())
      $scope.selectAll = () ->
        allSelected = []
        (allSelected.push contentItem.id.name for contentItem in row.content when (contentItem.ct == 'seat-status' && contentItem.status == 'mine')) for row in $scope.rows
        $scope.selected = allSelected
        $scope.$emit(UPDATE_SEAT_SELECTION, $scope.selected)

      $scope.seatStatus = (seat) ->
        idx = $scope.selected.indexOf(seat)
        if (idx == -1)
            "mine"
        else
            "selected"

      fetchAndUpdate = ->
        $http.post(jsRoutes.controllers.Orders.ajaxFloorplan($scope.showId, $scope.orderId).url).success((response) -> $timeout(fetchAndUpdate, 5000); update(response)).error((response) -> $timeout(fetchAndUpdate, 5000); errorHandler(response))

      fetchAndUpdate()


CounterInput = angular.module("counterInput", [])

CounterInput.controller "CounterInputCtrl", ($scope, $http) ->
    $scope.values = { }
    $scope.maxQuota = 0
    $scope.usedQuota = 0

    $scope.increment = (index, max = 9) ->
        if isNaN($scope.totalUsed())
            $scope.values[index] = 0
        else
            if ($scope.totalUsed() < $scope.maxQuota) then $scope.values[index] = Math.min(++$scope.values[index],max)

    $scope.decrement = (index, min = 0) ->
        $scope.values[index] = Math.max(--$scope.values[index],min) || 0

    $scope.totalUsed = ->
        totalSelected = (value for index,value of $scope.values).reduce (t, s) -> t + s
        totalSelected + $scope.usedQuota

    $scope.isMaxQuotaSatisfied = ->
        $scope.totalUsed() <= $scope.maxQuota



Floorplan.directive 'orderInput', () ->
  restrict: 'EA'
  template: """
<input name="seatTypes[{{$index}}]" type="hidden" value="{{seatType}}" data-ng-repeat="seatType in seatTypesArray">
<input name="priceCategory" type="hidden" value="{{priceCategory}}">
<input name="quantity" type="hidden" value="{{quantity}}">
<div class="quantity">{{quantity}}</div>
<div class="btn-group-vertical inline">
    <button type="button" class="btn btn-primary btn-xs" data-ng-click="increment()"><span class="glyphicon glyphicon-plus"></span></button>
    <button type="button" class="btn btn-primary btn-xs" data-ng-click="decrement()"><span class="glyphicon glyphicon-minus"></span></button>
</div>
<div class="div-inline">
    <button type="submit" class="btn btn-primary btn-sm" data-ng-disabled="!validQuantity()">Plaatsen kiezen &raquo;</button>
</div>
"""
  scope:
    quantity: '@'
    max: '@'
    priceCategory: '@'
    seatTypes: '@'
  controller: ($scope) ->
    $scope.seatTypesArray = eval($scope.seatTypes)
    $scope.quantity = parseInt($scope.quantity)
    $scope.increment = ->
      if (parseInt($scope.quantity) < parseInt($scope.max))
        $scope.quantity = parseInt($scope.quantity) + 1
    $scope.decrement = ->
      if ($scope.quantity > 0)
        $scope.quantity -= 1
    $scope.validQuantity = ->
      parseInt($scope.quantity) > 0 && parseInt($scope.quantity) <= parseInt($scope.max)
    $scope.$on(UPDATE_DISABLED_SEAT_TYPE, (event, addDisabledSeatType) -> $scope.updateDisabledSeatType(addDisabledSeatType))
    $scope.updateDisabledSeatType = (addDisabledSeatType) ->
      idx = $scope.seatTypesArray.indexOf("disabled")
      if (addDisabledSeatType && idx == -1)
          $scope.seatTypesArray.push("disabled")
      else if (!addDisabledSeatType && idx != -1)
          $scope.seatTypesArray.splice(idx, 1)


UPDATE_DISABLED_SEAT_TYPE = "UPDATE_DISABLED_SEAT_TYPE"
Floorplan.controller "OrderCtrl", ($scope, $http, $timeout) ->
  $scope.updateDisabledSeatType = () ->
    $scope.$broadcast(UPDATE_DISABLED_SEAT_TYPE, $scope.disabledSeatType)

DismissModal = angular.module("dismissModal", ["ng", "ui.bootstrap"])

DismissModal.controller "DismissModalCtrl", ($scope, $modal, $log) ->
    $scope.open = (confirmUrl, message) ->
        modalInstance = $modal.open({
            templateUrl: 'dismissModalContent.html',
            controller: "ModalInstanceCtrl",
            resolve: {
                message: ->
                    message
                confirmUrl: ->
                    confirmUrl
            }
        })

#--- Please note that $modalInstance represents a modal window (instance) dependency.
#--- It is not the same as the $modal service used above.

DismissModal.controller "ModalInstanceCtrl", ($scope, $modalInstance, message, confirmUrl) ->
  $scope.message = message
  $scope.confirmUrl = confirmUrl

  $scope.ok = ->
    $modalInstance.close()

  $scope.cancel = ->
    $modalInstance.dismiss('cancel')