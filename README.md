# AWS Lambda connector

The [AWS Lambda](https://aws.amazon.com/lambda/) sink connector is a [Pulsar IO connector](http://pulsar.apache.org/docs/en/next/io-overview/) for sending messages from Apache Pulsar to Amazon AWS Lambda.

Currently, AWS Lambda connector versions (`x.y.z`) are based on Pulsar versions (`x.y.z`).

| AWS Lambda connector version | Pulsar version | Doc |
| :---------- | :------------------- | :------------- |
[2.7.0](https://github.com/streamnative/pulsar-io-aws-lambda/releases/tag/v2.7.0)| [2.7.0](http://pulsar.apache.org/en/download/) | - [AWS lambda sink connector](#TBD)

## Project layout

Below are the sub folders and files of this project and their corresponding descriptions.

```bash

├── conf // examples of configuration files of this connector
├── docs // user guides of this connector
├── script // scripts of this connector
├── src // source code of this connector
│   ├── checkstyle // checkstyle configuration files of this connector
│   ├── license // license header for this project. `mvn license:format` can
    be used for formatting the project with the stored license header in this directory
│   │   └── ALv2
│   ├── main // main source files of this connector
│   │   └── java
│   ├── spotbugs // spotbugs configuration files of this connector
│   └── test // test related files of this connector
│       └── java

```

## License
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fstreamnative%2Fpulsar-io-aws-lambda.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2Fstreamnative%2Fpulsar-io-aws-lambda?ref=badge_large)
