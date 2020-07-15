resolvers in ThisBuild += "lightbend-commercial-mvn" at
  "https://repo.lightbend.com/pass/OOirva_2UzTNwbefTszwVzyeFw-pauiZy-rY5zhFQ7GGOGDY/commercial-releases"
resolvers in ThisBuild += Resolver.url("lightbend-commercial-ivy",
  url("https://repo.lightbend.com/pass/OOirva_2UzTNwbefTszwVzyeFw-pauiZy-rY5zhFQ7GGOGDY/commercial-releases"))(Resolver.ivyStylePatterns)