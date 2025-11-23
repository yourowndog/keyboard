{ pkgs, ... }: {
  channel = "stable-23.11";
  packages = [
    pkgs.jdk17
    pkgs.gradle
    pkgs.android-tools
  ];
  idx = {
    extensions = [
      "vscjava.vscode-java-pack"
      "vscjava.vscode-java-debug"
      "vscjava.vscode-gradle"
    ];
    previews = {
      enable = true;
      previews = {
        android = {
          command = ["./gradlew" "installDebug"];
          manager = "android";
        };
      };
    };
  };
}
