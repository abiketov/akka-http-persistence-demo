lazy val akkaHttpVersion = "10.1.8"
lazy val akkaVersion     = "2.5.23"
lazy val leveldbVersion = "0.9"
lazy val leveldbjniVersion = "1.8"

lazy val root = (project in file(".")).
  
  settings(
    inThisBuild(List(
      organization    := "org.demo.example",
      scalaVersion    := "2.12.8"
    )),
    
    name := "wallet",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"             % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence"       % akkaVersion,
      "com.typesafe.akka" %% "akka-http"              % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json"   % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-xml"          % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream"            % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query" % "2.5.23",

      "org.iq80.leveldb"            % "leveldb"          % leveldbVersion,
      "org.fusesource.leveldbjni"   % "leveldbjni-all"   % leveldbjniVersion,
      
      "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-testkit"         % akkaVersion     % Test,
      "com.typesafe.akka" %% "akka-stream-testkit"  % akkaVersion     % Test,
      "org.scalatest"     %% "scalatest"            % "3.0.5"         % Test,
     
      
    )
  )
mainClass in (Compile, run) := Some("org.demo.example.controller.RestApiServer")
parallelExecution in Test := false
fork in run := true
