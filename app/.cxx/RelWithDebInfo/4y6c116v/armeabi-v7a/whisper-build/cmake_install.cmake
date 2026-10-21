# Install script for directory: /home/declan/Documents/Projects/Products/01_Persnal_Work/call_record/app/build/generated/whisper/whisper.cpp-1.9.5

# Set the install prefix
if(NOT DEFINED CMAKE_INSTALL_PREFIX)
  set(CMAKE_INSTALL_PREFIX "/usr/local")
endif()
string(REGEX REPLACE "/$" "" CMAKE_INSTALL_PREFIX "${CMAKE_INSTALL_PREFIX}")

# Set the install configuration name.
if(NOT DEFINED CMAKE_INSTALL_CONFIG_NAME)
  if(BUILD_TYPE)
    string(REGEX REPLACE "^[^A-Za-z0-9_]+" ""
           CMAKE_INSTALL_CONFIG_NAME "${BUILD_TYPE}")
  else()
    set(CMAKE_INSTALL_CONFIG_NAME "RelWithDebInfo")
  endif()
  message(STATUS "Install configuration: \"${CMAKE_INSTALL_CONFIG_NAME}\"")
endif()

# Set the component getting installed.
if(NOT CMAKE_INSTALL_COMPONENT)
  if(COMPONENT)
    message(STATUS "Install component: \"${COMPONENT}\"")
    set(CMAKE_INSTALL_COMPONENT "${COMPONENT}")
  else()
    set(CMAKE_INSTALL_COMPONENT)
  endif()
endif()

# Install shared libraries without execute permission?
if(NOT DEFINED CMAKE_INSTALL_SO_NO_EXE)
  set(CMAKE_INSTALL_SO_NO_EXE "1")
endif()

# Is this installation the result of a crosscompile?
if(NOT DEFINED CMAKE_CROSSCOMPILING)
  set(CMAKE_CROSSCOMPILING "TRUE")
endif()

# Set default install directory permissions.
if(NOT DEFINED CMAKE_OBJDUMP)
  set(CMAKE_OBJDUMP "/home/declan/Android/Sdk/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-objdump")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib" TYPE STATIC_LIBRARY FILES "/home/declan/Documents/Projects/Products/01_Persnal_Work/call_record/app/.cxx/RelWithDebInfo/4y6c116v/armeabi-v7a/whisper-build/src/libwhisper.a")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/include" TYPE FILE FILES "/home/declan/Documents/Projects/Products/01_Persnal_Work/call_record/app/build/generated/whisper/whisper.cpp-1.9.5/include/whisper.h")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib" TYPE STATIC_LIBRARY FILES "/home/declan/Documents/Projects/Products/01_Persnal_Work/call_record/app/.cxx/RelWithDebInfo/4y6c116v/armeabi-v7a/whisper-build/src/libparakeet.a")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/include" TYPE FILE FILES "/home/declan/Documents/Projects/Products/01_Persnal_Work/call_record/app/build/generated/whisper/whisper.cpp-1.9.5/include/parakeet.h")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/whisper" TYPE FILE FILES
    "/home/declan/Documents/Projects/Products/01_Persnal_Work/call_record/app/.cxx/RelWithDebInfo/4y6c116v/armeabi-v7a/whisper-build/whisper-config.cmake"
    "/home/declan/Documents/Projects/Products/01_Persnal_Work/call_record/app/.cxx/RelWithDebInfo/4y6c116v/armeabi-v7a/whisper-build/whisper-config-version.cmake"
    )
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/pkgconfig" TYPE FILE FILES "/home/declan/Documents/Projects/Products/01_Persnal_Work/call_record/app/.cxx/RelWithDebInfo/4y6c116v/armeabi-v7a/whisper-build/whisper.pc")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/parakeet" TYPE FILE FILES
    "/home/declan/Documents/Projects/Products/01_Persnal_Work/call_record/app/.cxx/RelWithDebInfo/4y6c116v/armeabi-v7a/whisper-build/parakeet-config.cmake"
    "/home/declan/Documents/Projects/Products/01_Persnal_Work/call_record/app/.cxx/RelWithDebInfo/4y6c116v/armeabi-v7a/whisper-build/parakeet-config-version.cmake"
    )
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/pkgconfig" TYPE FILE FILES "/home/declan/Documents/Projects/Products/01_Persnal_Work/call_record/app/.cxx/RelWithDebInfo/4y6c116v/armeabi-v7a/whisper-build/parakeet.pc")
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for each subdirectory.
  include("/home/declan/Documents/Projects/Products/01_Persnal_Work/call_record/app/.cxx/RelWithDebInfo/4y6c116v/armeabi-v7a/whisper-build/ggml/cmake_install.cmake")
  include("/home/declan/Documents/Projects/Products/01_Persnal_Work/call_record/app/.cxx/RelWithDebInfo/4y6c116v/armeabi-v7a/whisper-build/src/cmake_install.cmake")

endif()

