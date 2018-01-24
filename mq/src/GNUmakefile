#
# Copyright (c) 2003, 2017 Oracle and/or its affiliates. All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v. 2.0, which is available at
# http://www.eclipse.org/legal/epl-2.0.
#
# This Source Code may also be made available under the following Secondary
# Licenses when the conditions for such availability set forth in the
# Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
# version 2 with the GNU Classpath Exception, which is available at
# https://www.gnu.org/software/classpath/license.html.
#
# SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
#

TOPDIR		= ./..
MAKEDEFS 	= $(TOPDIR)/src/buildcfg/Defs.gmk
INSTALLDEFS 	= $(TOPDIR)/src/buildcfg/Install.gmk
SUBDIRS 	= share $(ARCH)

include $(MAKEDEFS)
include $(INSTALLDEFS)

all optimized debug clean: create_links $(SUBDIRS) 

install:  doinstall
install-c:  doinstall-c
install-ri:  doinstall-ri
ips:  doipsstage
ias:  doias
fb: dofb 
fbv3: dofbv3
ias-l10n:  doias-l10n
l10n-install:  dol10n-install

create_links:
	@if [ $(PLATFORM) != Windows_NT -a $(PLATFORM) != SunOS ]; then \
	    rm -f $(PLATFORM); \
	    ln -s solaris $(PLATFORM); \
	fi;

#
# Any of these targets will build the C-API
# This target is mainly here for the windows build because
# we don't build C-API by default on windows since not everyone
# has the correct C compiler.
# For Solaris and Linux, this is built by default from the top.
#
c capi c-api cclient: do-c do-c64

do-c c32:
	cd '$(CCLIENTDIR)'; $(MAKE); 
	cd '$(CCLIENTDIR)'; $(MAKE) debug; 

clean-c:
	cd '$(CCLIENTDIR)'; $(MAKE) clean; 
	cd '$(CCLIENTDIR)'; $(MAKE) DATA_MODEL_64=1 clean; 

#
# Build 64-bit C-API libs on only.
#
do-c64 c64 capi64 c-api64 cclient64:
	cd '$(CCLIENTDIR)'; \
	$(MAKE) DATA_MODEL_64=1; \
	$(MAKE) DATA_MODEL_64=1 debug; \
#
# Makes an RI src tree from an MQ product workspace in TOP/src.
# This should only be run once on a clean MQ product workspace.
# Note: It will not restore your original TOP/src directory.
#
ri-src:
	@if [ $(PLATFORM) != Windows_NT ]; then \
	    /bin/sh $(TOPDIR)/src/buildcfg/tools/ri/scripts/j2ee_ri_src.sh; \
	fi;
#
# This make the RI src and binary bundles that we deliver 
# after:  gnumake ri-src  (build ri src tree)
#  	  gnumake (compile ri src and make ri install image)	  
#  	  gnumake ri-bundles (builds ri src and binary bundles.
#
ri-bundles: dori-bundles

# These targets below should be done AFTER an optimized (all) build is
# built and a 'gnumake install' image is done.
# The targets 'package', 'packages', 'bundles' are all the same.
# Do RPMs only on sparc and if the user is root or nakata or BUILD_RPMS=true
#   RPMs won't build for other users unless setup is correct in
#   $TOP/src/buildcfg/install/linux/GNUmakefile.
#
package packages bundles l10n-bundles: fb
	cd '$(PKGMAKEDIR)'; $(MAKE) $@ || exit 1; cd ..;
	@if [ "$(ARCH_TYPE)" = "sparc" ]; then \
	    cd '$(RPMMAKEDIR)'; $(MAKE) $@ || exit 1; cd ..; \
	fi;

bundles.installer: dofbv3
	cd '$(PKGMAKEDIR)'; $(MAKE) $@ || exit 1; cd ..;
	@if [ "$(ARCH_TYPE)" = "sparc" ]; then \
	    cd '$(RPMMAKEDIR)'; $(MAKE) $@ || exit 1; cd ..; \
	fi;

patches patch:
	cd '$(PATCHMAKEDIR)'; $(MAKE) $@ || exit 1; cd ..;
#
# The target 'eabundles' will make a tar.Z (solaris) file or a .zip (win32)
# of the install image and place the file in $TOP/dist/bundles.
# This target should be used after an optimized build is built and a 
# 'gnumake install' image is done.  The final tar.Z or .zip
# file can be used to distribute an Early Access/Alpha release
# until formal packaging/install shield bundles are in place.
#
eabundles: doeabundles

#
# Generate both public and private javadocs for JMQ.
# Output goes to src/doc/en/develop.
#
docs: 
	cd '$(_JAVA_SRC)'; $(MAKE) docs || exit 1; cd ..;

clobber:  doclobber
	cd '$(EXAMPLES_SRC)'; $(MAKE) clean || exit 1; cd ..;


#
# This target is only for RE to be used on Solaris10 x86 w/amd64 support
# Reason for this target:  Solaris x86 32-bit C libs need to be
# built on S9 while Solaris x86 64-bit (amd64) C libs needs to be
# built on S10.
#
# When using this target, it assumes you already built 32-bit C libs
# both libimqutil.so and libmqcrt.so on Solaris9.
# Then it saves the 32-bits libs on Solaris 9 away
# and builds on Solaris10 and saves the amd64 libs.
# Finally, it restores the S9 libs and objs.
#
# Note: This target is fragile and assume a bunch of
# files, libs, directory already exist.  If you get an
# error during this amd64 target, it is best to
# go back to the S9 system, do a clean and do a make.
# Then come back to this amd64 target on S10.
#
#
amd64:  
	@if [ "$(ARCH_TYPE)" = "i386" ]; then \
	   rm -rf $(TOPDIR)/binary/solaris/opt/obj/native/amd64; \
	   rm -rf $(TOPDIR)/binary/solaris/opt/lib/amd64/libimqutil.so.1; \
	   cd $(TOPDIR)/src/solaris; $(MAKE) c-lib64; cd ../; \
	fi; 

amd64-old:  
	@if [ "$(ARCH_TYPE)" = "i386" ]; then \
	   mv -f $(TOPDIR)/binary/solaris/opt/lib/libimqutil.so.1 $(TOPDIR)/binary/solaris/opt/lib/libimqutil.so.1-s9; \
	   mv -f $(TOPDIR)/binary/solaris/opt/lib/amd64/libimqutil.so.1 $(TOPDIR)/binary/solaris/opt/lib/amd64/libimqutil.so.1-s9; \
	   mv -f $(TOPDIR)/binary/solaris/opt/lib/libmqcrt.so.1 $(TOPDIR)/binary/solaris/opt/lib/libmqcrt.so.1-s9; \
	   mv -f $(TOPDIR)/binary/solaris/opt/lib/amd64/libmqcrt.so.1 $(TOPDIR)/binary/solaris/opt/lib/amd64/libmqcrt.so.1-s9; \
	   \
	   \
	   rm -rf $(TOPDIR)/binary/solaris/opt/obj/native-s9; \
	   rm -rf $(TOPDIR)/binary/solaris/opt/obj/cclient-s9; \
	   mv -f $(TOPDIR)/binary/solaris/opt/obj/native $(TOPDIR)/binary/solaris/opt/obj/native-s9; \
	   mv -f $(TOPDIR)/binary/solaris/opt/obj/cclient $(TOPDIR)/binary/solaris/opt/obj/cclient-s9; \
	   cd $(TOPDIR)/src/solaris/native; $(MAKE); cd ../; $(MAKE) c-lib64; cd ../; \
	   cd '$(CCLIENTDIR)'; $(MAKE); $(MAKE) DATA_MODEL_64=1; $(MAKE) DATA_MODEL_64=1 debug; cd ../..; \
	   cp -fp $(TOPDIR)/binary/solaris/opt/lib/libimqutil.so.1 $(TOPDIR)/binary/solaris/opt/lib/libimqutil.so.1-s10; \
	   cp -fp $(TOPDIR)/binary/solaris/opt/lib/amd64/libimqutil.so.1 $(TOPDIR)/binary/solaris/opt/lib/amd64/libimqutil.so.1-s10; \
	   cp -fp $(TOPDIR)/binary/solaris/opt/lib/libmqcrt.so.1 $(TOPDIR)/binary/solaris/opt/lib/libmqcrt.so.1-s10; \
	   cp -fp $(TOPDIR)/binary/solaris/opt/lib/amd64/libmqcrt.so.1 $(TOPDIR)/binary/solaris/opt/lib/amd64/libmqcrt.so.1-s10; \
	   \
	   \
	   rm -rf $(TOPDIR)/binary/solaris/opt/obj/native; \
	   rm -rf $(TOPDIR)/binary/solaris/opt/obj/cclient; \
	   mv -f $(TOPDIR)/binary/solaris/opt/obj/native-s9 $(TOPDIR)/binary/solaris/opt/obj/native; \
	   mv -f $(TOPDIR)/binary/solaris/opt/obj/cclient-s9 $(TOPDIR)/binary/solaris/opt/obj/cclient; \
	   mv -f $(TOPDIR)/binary/solaris/opt/lib/libimqutil.so.1-s9 $(TOPDIR)/binary/solaris/opt/lib/libimqutil.so.1; \
	   mv -f $(TOPDIR)/binary/solaris/opt/lib/amd64/libimqutil.so.1-s9 $(TOPDIR)/binary/solaris/opt/lib/amd64/libimqutil.so.1; \
	   mv -f $(TOPDIR)/binary/solaris/opt/lib/libmqcrt.so.1-s9 $(TOPDIR)/binary/solaris/opt/lib/libmqcrt.so.1; \
	   mv -f $(TOPDIR)/binary/solaris/opt/lib/amd64/libmqcrt.so.1-s9 $(TOPDIR)/binary/solaris/opt/lib/amd64/libmqcrt.so.1; \
	fi; 

findbugs: findbugs-report
