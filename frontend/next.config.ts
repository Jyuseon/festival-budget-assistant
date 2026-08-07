import path from "node:path";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 상위 홈 디렉터리의 lockfile을 워크스페이스 루트로 잘못 인식하는 것을 방지
  turbopack: {
    root: path.resolve(__dirname),
  },
};

export default nextConfig;
