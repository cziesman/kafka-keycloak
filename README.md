## Kafka mTLS with Keycloak Authentication Demo

This project provides a very simple demonstration of a Kafka broker that uses Keycloak for client authentication and a Java Kafka client that connects to a Kafka broker using mutual TLS (mTLS).

It depends on the *spring-kafka* library to provide the plumbing needed to connect to the Kafka broker and to send and receive messages.

It also uses the *openshift-maven-plugin* to simplify deployment of the demo application to an Openshift project.

### Assumptions

The demo assumes that the _AMQ Streams_ operator is used to manage Kafka, the _Red Hat Single Sign-On_ operator is used to manage Keycloak, and that the developer can login to Openshift with access to Kafka and Keycloak resources.

The demo application is deployed into its own project. In this case we use *kafka-client* for the project name. Kafka is deployed in the *kafka* project and Keycloak is deployed in the *keycloak* project.

The demo application uses a topic named *my-topic*. This topic should be created in Kafka using the _AMQ Streams_ operator.

### Keycloak Configuration

A Keycloak instance needs to be created in the *keycloak* project using the _Red Hat Single Sign-On_ operator. For the purposes of this demo, the default settings are mostly sufficient, but use the name *kafka-keycloak* for the Keycload instance. The operator will create an ephemeral instance of Keycloak, meaning that any configuration changes will be lost if the Keycloak pod is deleted for any reason.

The operator will create a Secret named *credential-kafka-keycloak*. This Secret contains the admin username and password needed to login to the Keycloak web console. The operator will also create a Route named *keycloak* that provides the URL of the Keycloak web console.

Use the web console to create a new realm named *kafka-authz*. For the purposes of this demo, the default settings are sufficient.

Create two new clients in the *kafka-authz* realm: *kafka-broker-service-account* and *kafka-client-service-account*. For both clients, set the *Access Type* to `confidential`, set *Standard Flow Enabled* to `OFF`, and set *Service Accounts Enabled* to `ON`. Under *Advanced Settings*, set *Access Token Lifespan* to `10 Minutes`. Under the *Credentials* tab for each client, make a note of the *Secret* for each client. Use the following commands with the previously noted client credential secrets to create the corresponding client secrets in Openshift:

    oc create secret generic kafka-broker-service-account --from-literal clientSecret=<kafka-broker-service-account-secret> -n kafka
    oc create secret generic kafka-client-service-account --from-literal clientSecret=<kafka-client-service-account-secret -n kafka

### Kafka Configuration

In order to allow access to Kafka from outside Openshift, a route needs to be defined. To create the necessary route, update the *listeners* section of the YAML file for the cluster with the following snippet. Since Keycloak authentication will be used, the listeners for both ports 9093 and 9094 need to include the authentication details. In this case, the cluster is named *my-cluster*.

    - name: tls
      port: 9093
      tls: true
      type: internal
      authentication:
        clientId: kafka-broker-service-account
        clientSecret: 
          secretName: kafka-broker-service-account
          key: clientSecret
        introspectionEndpointUri: 'https://<keycloak-route>/auth/realms/kafka-authz/protocol/openid-connect/token/introspect'
        maxSecondsWithoutReauthentication: 3600
        type: oauth
        userNameClaim: preferred_username
        validIssuerUri: 'https://<keycloak-route>/auth/realms/kafka-authz'
    - name: external
      port: 9094
      tls: true
      type: route
      authentication:
        clientId: kafka-broker-service-account
        clientSecret: 
          secretName: kafka-broker-service-account
          key: clientSecret
        introspectionEndpointUri: 'https://<keycloak-route>/auth/realms/kafka-authz/protocol/openid-connect/token/introspect'
        maxSecondsWithoutReauthentication: 3600
        type: oauth
        userNameClaim: preferred_username
        validIssuerUri: 'https://<keycloak-route>/auth/realms/kafka-authz'

The name for the listener on port 9094 is arbitrary. Here we use *external* to indicate that the listener is for clients that are external to Openshift.

A Kafka topic called `my-topic` needs to be created with ten partitions. The YAML should appear similar to the following:

    apiVersion: kafka.strimzi.io/v1beta2
    kind: KafkaTopic
    metadata:
      labels:
        strimzi.io/cluster: my-cluster
      name: my-topic
      namespace: kafka
    spec:
      config: {}
      partitions: 10
      replicas: 3

The next step is to create a Kafka User using the _AMQ Streams_ operator. Create a `kafka-user` in the `kafka-cluster`. The authentication type must be `tls`. The YAML should appear similar to the following:

    apiVersion: kafka.strimzi.io/v1beta2
    kind: KafkaUser
    metadata:
      name: kafka-user
      labels:
        strimzi.io/cluster: my-cluster
    spec:
      authentication:
        type: tls

### Truststore and Keystore

The cluster will also have a certificate defined in a Secret. This demo assumes that the default self-signed certificate is used. In this case, the secret is named _my-cluster-cluster-ca-cert_.

Use the following commands to extract the cluster truststore and associated password.

    oc get secret my-cluster-cluster-ca-cert -n kafka -o jsonpath='{.data.ca\.p12}' | base64 -d > kafka-cluster-ca.p12
    
    oc get secret my-cluster-cluster-ca-cert -n kafka -o jsonpath='{.data.ca\.password}' | base64 -d

Use the following commands to extract the user keystore and associated password.

    oc get secret kafka-user -n kafka -o jsonpath='{.data.user\.p12}' | base64 -d > kafka-user.p12
    oc get secret kafka-user -n kafka -o jsonpath='{.data.user\.password}' | base64 -d

When running the demo on a local machine, the truststore and keystore files need to be accessible. For this demo, they are placed in the `src/main/resources/certs` directory and the paths are configured for Kafka in `application.yaml`. The passwords that were extracted are also configured in `application.yaml`. Note that in a production environment, those passwords would be stored in a Secret or retrieved from a secure application like Vault.

In order to deploy the client application on Openshift, the truststore and keystore must be available via a Secret. Luckily, the openshift-maven-plugin makes this easy. Use the following commands to convert the truststore and keystore files into secrets that can be configured in a template for use by the openshift-maven-plugin:

    oc create secret generic dontcare --from-file=./src/main/resources/certs/kafka-cluster-ca.p12 -o yaml --dry-run=client
    oc create secret generic dontcare --from-file=./src/main/resources/certs/kafka-user.p12 -o yaml --dry-run=client

Use the YAML output from the commands to create the definition for a Secret named *kafka-client-secret*, which can be found in the file `src/main/resources/secret.yaml`.

Create the secret:

    oc apply -f src/main/resources/secret.yaml -n kafka

### Local Deployment

The `application.yaml` file needs to be updated with a few values in order for the application to run successfully.

Set the values for the Kafka bootstrap server and the Keycloak server.

    kafka-server: my-cluster-kafka-bootstrap-kafka.apps.cluster-psfnh.dynamic.redhatworkshops.io:443
    keycloak-server: https://keycloak-keycloak.apps.cluster-psfnh.dynamic.redhatworkshops.io

Set the value for the Keycloak `client-secret`.

    client-secret: kuKTlbYAZwz00O3nGlK2O0CEbk33YC7H


Set the passwords for the new truststore and keystore.

        truststore:
          location: src/main/resources/certs/kafka-cluster-ca.p12
          password: Y5cphBpQPqiJ
          type: PKCS12
        keystore:
          location: src/main/resources/certs/kafka-user.p12
          password: rrtSZaUJ1vJ4pZxkslxhnU5EnDYIr6pN
          type: PKCS12


The demo client application is deployed using the following command:

    mvn clean spring-boot:run

This command will compile the code, build a JAR file, and run the JAR file.

Once the application is running, it can be tested using a command similar to the following:

    curl http://localhost:8080/api/send?message=hello

If the message is sent, the response should be `Sent [hello]`.

### Openshift Deployment

The openshift-maven-plugin uses the file `src/main/jkube/deploymentconfig.yaml` to extract the data from the *kafka-client-secret* and to mount the truststore file and the keystore file at a filesystem location where they are accessible by the demo client application.

The demo client application is deployed using the following command:

    mvn clean oc:build oc:deploy

This command will run an S2I build on Openshift to compile the code, build a JAR file, create an image, push the image to Openshift, and deploy the image.

Once the application is running, it can be tested using a command similar to the following:

    curl http://client-kafka-client.apps.cluster-lb1614.lb1614.sandbox2566.opentlc.com/api/send?message=hello

If the message is sent, the response should be `Sent [hello]`.

