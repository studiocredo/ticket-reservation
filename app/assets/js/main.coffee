"use strict"
App = angular.module("credo", ["floorplan", "counterInput"])

Floorplan = angular.module("floorplan", ["ngDragDrop", "ui.sortable", "ng"])

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
                <div class="spacer spacer-{{content.width}}" data-ng-if="content.ct == 'spacer'"></div>
                <div class="clickable seat seat-{{content.kind}}" data-ng-if="content.ct == 'seat'">{{content.id.name}}</div>
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
                <div class="spacer spacer-{{content.width}}" data-ng-if="content.ct == 'spacer'"></div>
                <div class="seat seat-{{content.kind}}" data-ng-if="content.ct == 'seat'">{{content.id.name}}</div>
                <div class="seat seat-status-{{content.status}}" data-ng-if="content.ct == 'seat-status'">{{content.id.name}}</div>
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
     $http.get(jsRoutes.controllers.Events.ajaxOrderAdminFloorplan($scope.show).url).success (plan) ->
             $scope.plan = plan
             $scope.rows = plan.rows

Floorplan.directive 'orderAdminFloorplan', () ->
    restrict: 'EA'
    template: """
<div class="fp fp-fancy">
    <div class="row seat-row vspacer-{{row.vspace}}" data-ng-repeat="row in rows">
        <div data-ng-model="row.content" class="span-12">
            <div class="content" data-ng-repeat="content in row.content">
                <div class="spacer spacer-{{content.width}}" data-ng-if="content.ct == 'spacer'"></div>
                <div class="seat seat-{{content.kind}}" data-ng-if="content.ct == 'seat'">{{content.id.name}}</div>
                <div class="seat seat-status-{{content.status}}" data-ng-if="content.ct == 'seat-status'">{{content.id.name}}</div>
            </div>
        </div>
    </div>
    <p class="text-center">Podium</p>
</div>
"""
    scope:
        show: '='
    controller: 'OrderAdminFloorpanCtrl'

CounterInput = angular.module("counterInput", [])

CounterInput.controller "CounterInputCtrl", ($scope, $http) ->
    $scope.values = { }
    $scope.maxQuota = 0

    $scope.increment = (index, max = 9) ->
        if isNaN($scope.totalUsed())
            $scope.values[index] = 0
        else
            if ($scope.totalUsed() < $scope.maxQuota) then $scope.values[index] = Math.min(++$scope.values[index],max)

    $scope.decrement = (index, min = 0) ->
        $scope.values[index] = Math.max(--$scope.values[index],min) || 0

    $scope.totalUsed = ->
        (value for index,value of $scope.values).reduce (t, s) -> t + s

    $scope.isMaxQuotaSatisfied = ->
        $scope.totalUsed() <= $scope.maxQuota