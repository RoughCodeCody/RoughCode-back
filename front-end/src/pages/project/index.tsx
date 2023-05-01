import Head from "next/head";
import { Projects } from "@/features/projects";

import { useRouter } from "next/router";

export default function Project() {
  return (
    <>
      <Head>
        <title>Create Next App</title>
        <meta name="description" content="Generated by create next app" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <link rel="icon" href="/favicon.ico" />
      </Head>

      <Projects />
    </>
  );
}
