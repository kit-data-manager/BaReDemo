# Basic Repository Demonstrator

The Basic Repository Demonstrator (BaReDemo) serves as showcase for highly customized repository solutions. It allows to manage digital objects including Dublin Core metadata. An easy-to-use Web interface allows to modify and to retrieve digital objects in a Google-like 
simplicity. For more information on components of the BaReDemo and how to use it, please refer to the documentation, which is also part of this project.

## How to build

In order to build the BaReDemo you'll need:

* Java SE Development Kit 7 or higher
* Apache Maven 3
* Build of the [KIT Data Manager 1.2 base](https://github.com/kit-data-manager/base) project

After building KIT Data Manager 1.2 base and obtaining the sources of the BaReDemo, change to the folder where the sources are located, e.g. /home/user/BaReDemo. Edit the file `makeDist.sh` and set JAVA_HOME and MAVEN_HOME according to your local installation. Afterwars, just call:

```
user@localhost:/home/user/BaReDemo/$ chmod +x makeDist.sh
user@localhost:/home/user/BaReDemo/$ ./makeDist.sh
Building distribution for release BaReDemo-1.0-SNAPSHOT and application BaReDemo.war
Executing clean install
[...]
user@localhost:/home/user/BaReDemo/$
```

As soon as the build process has finished there will be a file named `BaReDemo-1.0.zip` located at /home/user/BaReDemo which is the distribution package of the BaReDemo containing everything you need to launch the demonstrator. Extract the zip file to a directory of your choice and refer to the contained manual for further instructions.

## More Information

* [Project homepage](http://datamanager.kit.edu/index.php/kit-data-manager)
* [Manual](http://datamanager.kit.edu/dama/manual/index.html)
* [Bugtracker](http://datamanager.kit.edu/bugtracker/thebuggenie/)

## License

The Basic Repository Demonstrator is licensed under the Apache License, Version 2.0.


