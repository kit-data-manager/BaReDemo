#!/bin/sh
export VERSION=BaReDemo-1.0
#echo Checking out Tagged version $VERSION
#svn switch svn+ssh://ipepc21.ka.fzk.de/srv/svn/KDM_EXT/tags/$VERSION/

export JAVA_HOME=
export MAVEN_HOME=
export DEPLOY_TARGET=docker
export MAVEN_OPTS="-DskipTests=true -DassembleMode=true -Ddeploy.target=$DEPLOY_TARGET"
export RELEASE_NAME=BaReDemo-1.0-SNAPSHOT
export APPLICATION_PACKAGE=BaReDemo.war
export APPLICATION_ASSEMBLY_NAME=baredemo
export PATH=$PATH:$MAVEN_HOME/bin

# Process result handler used to check the status of the previous build step
handle_result(){
 if [ "X$1" != "X0" ] ; then
    echo $2
    exit 1
  fi
}

echo "Building distribution for release $RELEASE_NAME and application $APPLICATION_PACKAGE"

echo "Executing clean install"
$MAVEN_HOME/bin/mvn $MAVEN_OPTS clean install
handle_result $? "Failed to create assembly"

echo Building assembly
$MAVEN_HOME/bin/mvn $MAVEN_OPTS assembly:single -N
handle_result $? "Failed to create assembly"

echo Merging settings into $APPLICATION_PACKAGE
cp ./assembly/$RELEASE_NAME-$APPLICATION_ASSEMBLY_NAME/$RELEASE_NAME/$APPLICATION_PACKAGE ./assembly/$APPLICATION_PACKAGE
handle_result $? "Failed to perform assembly"
  
cd assembly/$RELEASE_NAME-settings/$RELEASE_NAME
jar -uvf ../../$APPLICATION_PACKAGE WEB-INF
handle_result $? "Failed to create application package."

cd ../../../

mkdir -p assembly/package/BaReDemo/manual

cp -r ./Docker/* assembly/package/BaReDemo/
cp -r ./assembly/$RELEASE_NAME-$APPLICATION_ASSEMBLY_NAME/$RELEASE_NAME/manual/* assembly/package/BaReDemo/manual
cp ./assembly/$APPLICATION_PACKAGE assembly/package/BaReDemo/tomcat/BaReDemo.war

cd assembly/package/BaReDemo

echo "Building ZIP $VERSION.zip"
jar -cfM $VERSION.zip *
handle_result $? "Failed zip final package."

echo "Moving $VERSION.zip to current directory"  
mv $VERSION.zip ../../..
echo "Removing $VERSION.zip"

cd ../../../

exit 0
