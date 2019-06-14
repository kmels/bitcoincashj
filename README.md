[![Build Status](https://travis-ci.org/kmels/blockchainj.png?branch=master)](https://travis-ci.org/kmels/blockchainj)   [![Coverage Status](https://coveralls.io/repos/github/kmels/blockchainj/badge.svg?branch=master)](https://coveralls.io/github/kmels/blockchainj?branch=release-0.15) [![Javadocs](http://www.javadoc.io/badge/org.bitcoincashj/bitcoincashj-core.svg)](http://www.javadoc.io/doc/blockchainj/blockchainj-core)

### Welcome to BlockChainJ

This library is a fork of Mike Hearn's original bitcoinj library aimed at supporting the Bitcoin and Bitcoin Cash eco-system.

It allows maintaining a BIP 47 wallet for sending/receiving transactions without needing a full blockchain node. It comes with full documentation and some example apps showing how to use it.

Release notes are [here](docs/Releases.md).

### Getting started

To get started, it is best to have the latest JDK (`openjdk`, `openjdk*openjfx`), `gradle` and `protoc` installed (`protobuf-compiler` in ubuntu/fedora).

The HEAD of the `release-0.15` branch contains the latest development code and various production releases are provided on feature branches.

#### Building from the command line

To perform a full build use
```
gradle clean build
```

To generate a jar bundle use
```
gradle clean fatJar
```

You can also run
```
gradle javadoc
```
to generate the JavaDocs.

The outputs are under the `build` directory.

### Example applications

These are found in the `examples` module.

### Where next?

Now you are ready to [follow the tutorial](https://bitcoinj.github.io/getting-started).

