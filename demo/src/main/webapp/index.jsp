<html ng-app>
	<head>
		<script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.2.5/angular.min.js"></script>
	</head>
	<body ng-controller="demoCtr">
		<h2>Prerender java demo page</h2>
		<p>use angularjs ng-repeat</p>
		<ul>
			<li ng-repeat="item in language">{{item}}</li>
		</ul>
		<script type="text/javascript">
		    function demoCtr($scope) {
		        $scope.language = ["C#", "javascript", "java", "python", "ruby"];
		    }
		</script>
	</body>
</html>
