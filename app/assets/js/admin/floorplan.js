'use strict';

var App = angular.module('floorplan', ['ngDragDrop', 'ui.sortable']);

App.controller('FloorPlanCtrl', function ($scope, $http) {
    var SEAT_NORMAL = "normal";
    var SEAT_VIP = "vip";
    var SEAT_DISABLED = "disabled";
    var PAINT_DELETE = "rm";

    $scope.messages = { };

    $scope.rows = [ ];

    $scope.init = function (venueId) {
        $scope.venueId = venueId;

        $http.get(jsRoutes.controllers.admin.Floorplans.ajaxFloorPlan($scope.venueId).url).success(function (plan) {
            $scope.plan = plan;
            $scope.rows = plan.rows;
        });
    };

    $scope.save = function () {
        $scope.messages = [];
        var call = $http.post(jsRoutes.controllers.admin.Floorplans.ajaxSaveFloorPlan($scope.venueId).url, $scope.plan);
        call.success(function (data) {
            $scope.messages.push({ type: "success", msg: "Saved."})
        });
        call.error(function (data) {
            $scope.messages.push({type: "danger", msg: "Failed to save"})
        });
    };

    // -------
    $scope.paint = SEAT_NORMAL;
    $scope.paintNormal = function () {
        $scope.paint = SEAT_NORMAL;
    };
    $scope.paintVip = function () {
        $scope.paint = SEAT_VIP;
    };

    $scope.paintDisabled = function () {
        $scope.paint = SEAT_DISABLED;
    };
    $scope.paintDelete = function() {
        $scope.paint = PAINT_DELETE
    };

    function removeContent(row, index) {
        row.content.splice(index, 1);
    }

    $scope.paintSpacer = function (row, index) {
        if ($scope.paint == PAINT_DELETE) {
            removeContent(row, index);
        }
    };

    $scope.paintSeat = function (row, index) {
        if ($scope.paint == PAINT_DELETE) {
            removeContent(row, index);
        } else {
            row.content[index].kind = $scope.paint;
        }
    };

    // -------
    $scope.addRow = function () {
        $scope.rows.push({content: [
            {ct: "seat", kind: SEAT_NORMAL}
        ]});
    };
    $scope.removeRow = function (index) {
        $scope.rows.splice(index, 1);
    };

    $scope.addSeat = function (row) {
        row.content.push({ct: "seat", kind: $scope.paint})
    };

    $scope.spacerSize = 2;
    $scope.addSpacer = function (row, size) {
        row.content.push({ct: "spacer", width: size})
    };

    $scope.editRow = function (row) {
        $scope.selectedRow = row;
    };

    $scope.rowSortableOptions = {
        axis: "x",
        cursor: "move",
        revert: true
    };

    $scope.range = function (n) {
        var list = [];
        for (var i = 0; i < n; i++) {
            list.push({});
        }
        return list
    };

});
