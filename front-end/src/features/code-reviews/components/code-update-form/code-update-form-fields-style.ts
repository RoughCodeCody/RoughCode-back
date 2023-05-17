import styled from "styled-components";

export const GitHubBtn = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 8rem;
  height: 4rem;
  border: none;
  border-radius: 5px;
  background-color: var(--main-color);
  cursor: pointer;
  padding: 0.5rem;
  color: var(--white-color);
  font-size: 1rem;

  &:disabled {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 8rem;
    height: 4rem;
    border: none;
    border-radius: 5px;
    background-color: var(--sub-one-color);
    cursor: not-allowed;
    color: var(--sub-two-color);
    font-size: 1rem;
  }
`;

export const SubmitButtonWrapper = styled.div`
  display: flex;
  flex-flow: column nowrap;
  align-items: center;
`;

export const SubmitButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 8rem;
  height: 4rem;
  border: none;
  border-radius: 5px;
  background-color: var(--main-color);
  cursor: pointer;
  padding: 0.5rem;
  color: var(--white-color);
  font-size: 1rem;

  &:disabled {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 8rem;
    height: 4rem;
    border: none;
    border-radius: 5px;
    background-color: var(--sub-one-color);
    cursor: not-allowed;
    color: var(--sub-two-color);
    font-size: 1rem;
  }
`;
