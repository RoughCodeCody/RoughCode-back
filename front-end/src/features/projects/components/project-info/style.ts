import styled from "styled-components";

const ProjectInfoWrapper = styled.div``;

const UrlApkBtn = styled.button<{ isClosed?: boolean }>`
  width: 100%;
  height: 5rem;
  background-color: ${({ isClosed }) =>
    isClosed ? "var(--sub-two-color)" : "var(--main-color)"};
  color: var(--sub-one-color);
  font-size: 2rem;
  font-weight: 700;
  margin-top: 0.3rem;
  cursor: pointer;

  &:hover {
    color: var(--white-color);
  }
`;

export { ProjectInfoWrapper, UrlApkBtn };